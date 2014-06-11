package com.qgymib.findthetoiletserver.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.qgymib.findthetoiletserver.ConfigData;
import com.qgymib.findthetoiletserver.data.DataTransfer.ToiletSetInfo;
import com.qgymib.findthetoiletserver.data.Database;
import com.qgymib.findthetoiletserver.support.Tools;

public class Task implements Runnable {

    private SocketChannel channel;
    private Socket clientSocket;
    private Selector selector;
    private static Map<SelectionKey, String> cachedMessage = null;
    /**
     * 数据库引用
     */
    private Database db = ConfigData.DataBase.getDatabase();
    /**
     * 用于向socket写数据，在任务开始时进行初始化， 在任务结束时销毁，参见{@link #call()}。
     */
    private PrintWriter writer = null;
    /**
     * 用于从socket读数据，在任务开始时进行初始化， 在任务结束时销毁，参见{@link #call()}。
     */
    private BufferedReader reader = null;
    /**
     * 最后一次接收到的信息。
     */
    private String lastReceivedMessage = null;
    /**
     * 最后一次发送的信息。
     */
    private String lastSendedMessage = null;

    public Task(SocketChannel sc, Selector selector) {
        channel = sc;
        clientSocket = sc.socket();
        this.selector = selector;
        if (cachedMessage == null) {
            cachedMessage = new HashMap<SelectionKey, String>();
        }
    }

    @Override
    public void run() {
        try {
            // 初始化任务所需资源
            initTask();

            // 接收数据包
            rcvPackage();

            if (checkMessage()) {
                // 信息校验成功，进行下一步处理
                System.out.println("信息校验成功");
            } else {
                // 信息校验失败
                System.out.println("信息校验失败");
                sendMessage(ConfigData.MessageType.LOST);
                return;
            }

            switch (getTaskType()) {
            // 用户请求断开连接
            case ConfigData.MessageType.FIN:
                System.out.println("FIN");
                actionForFin();
                cachedMessage.remove(channel.keyFor(selector));
                channel.keyFor(selector).cancel();
                channel.close();
                break;

            // 搜索请求
            case ConfigData.MessageType.SEARCH:
                actionForSearch();
                System.out.println("SEARCH");
                break;

            case ConfigData.MessageType.FIX:
                // TODO 修正信息请求
                System.out.println("FIX");
                break;

            case ConfigData.MessageType.INSERT:
                // TODO 插入请求
                System.out.println("INSERT");
                break;

            case ConfigData.MessageType.DELETE:
                // TODO 删除请求
                System.out.println("DELETE");
                break;

            // 注册请求
            case ConfigData.MessageType.SIGNUP:
                System.out.println("SIGNUP");
                actionForSignup();
                break;

            // 登录请求
            case ConfigData.MessageType.LOGIN:
                System.out.println("LOGIN");
                actionForLogin();
                break;

            // 报文损坏，请求重新发送报文
            case ConfigData.MessageType.LOST:
                System.out.println("LOST");
                sendCachedMessage();
                break;
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 清理任务占用资源
            purgeTask();
        }
    }

    /**
     * 响应Fin请求。报文如下： 0x00_CRC32:7212180a
     */
    private void actionForFin() {
        sendMessage(ConfigData.MessageType.FIN);
    }

    /**
     * 用户注册事件处理。报文为如下格式：
     * 0x05_qgymib_e10adc3949ba59abbe56e057f20f883e_CRC32:1278060152
     */
    private void actionForSignup() {
        String[] messageList = getMessageList();
        String username = messageList[0];
        String passwd_md5 = messageList[1];

        int result = db.signupTask(username, passwd_md5);
        sendMessage(ConfigData.MessageType.SIGNUP, String.valueOf(result));
    }

    /**
     * 用户搜索事件处理。报文为如下格式： 0x01_3648852058_CRC32:2424806038
     */
    private void actionForSearch() {
        ToiletSetInfo info = db.searchTask(getMessageList()[0]);

        if (info == null) {
            sendMessage(ConfigData.MessageType.SEARCH_VERSION, "-1");
            sendMessage(ConfigData.MessageType.SEARCH_VALUE, "");
        } else {
            sendMessage(ConfigData.MessageType.SEARCH_VERSION, ""
                    + info.version);
            sendMessage(ConfigData.MessageType.SEARCH_VALUE, info.value);
        }
    }

    /**
     * 用户登录事件处理。报文为如下格式：
     * 0x06_qgymib_e10adc3949ba59abbe56e057f20f883e_CRC32:1278060152
     */
    private void actionForLogin() {
        String[] messageList = getMessageList();
        String username = messageList[0];
        String passwd_md5 = messageList[1];

        int result = db.loginTask(username, passwd_md5);
        sendMessage(ConfigData.MessageType.LOGIN, String.valueOf(result));
    }

    /**
     * 初始化Task所需资源。此处抛出的异常必须被捕获，以便通知用户并作相应处理。
     * 
     * @throws IOException
     * @see {@link #run()}
     */
    private void initTask() throws IOException {
        // 初始化writer
        writer = new PrintWriter(new OutputStreamWriter(
                clientSocket.getOutputStream(), ConfigData.Net.encoding), false);
        // 初始化reader
        reader = new BufferedReader(new InputStreamReader(
                clientSocket.getInputStream(), ConfigData.Net.encoding));
    }

    /**
     * 释放Task占用的资源
     * 
     * @throws IOException
     */
    private void purgeTask() {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                reader = null;
            }
        }

        if (writer != null) {
            writer.close();
            writer = null;
        }
    }

    /**
     * 发送指定信息，并在指定信息后添加CRC32校验码。信息会被缓存，以便当服务器要求时立即进行重发，参见
     * {@link #sendCachedMessage()}
     * 
     * @param messageList
     *            MessageType + message1 + message2 ...
     * @see ConfigureInfo.message_type
     */
    private void sendMessage(int type, String... messageList) {
        // 封装任务类型
        String message = "0x0" + type + "_";
        // 计算数据长度
        int messageListSize = messageList.length;

        // 拼接字符串
        for (int i = 0; i < messageListSize; i++) {
            message += messageList[i];
            message += "_";
        }

        // 完成字符串的校验
        lastSendedMessage = message + "CRC32:"
                + Tools.getCRC32(message.substring(0, message.length() - 1));

        // 发送字符串
        writer.println(lastSendedMessage);
        writer.flush();

        System.out.println("Server send message:" + lastSendedMessage);

        cachedMessage.put(channel.keyFor(selector), lastSendedMessage);
    }

    /**
     * 立即发送缓存的信息
     */
    private void sendCachedMessage() {
        lastSendedMessage = cachedMessage.get(channel.keyFor(selector));

        System.out.println("Server send cached message:" + lastSendedMessage);
        writer.println(lastSendedMessage);
        writer.flush();
    }

    /**
     * 接收套接字上的报文。接收到的信息将会被缓存到{@link #lastReceivedMessage}中。
     * 
     * @throws IOException
     */
    private void rcvPackage() throws IOException {

        try {
            // 确保得到的不是空信息
            while (true) {
                lastReceivedMessage = reader.readLine();

                if (lastReceivedMessage != null) {
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("NetworkTaskManager:rcvPackage() 读取信息失败");
            throw e;
        }

        System.out.println("Server rcv msg:" + lastReceivedMessage);
    }

    /**
     * 检查接收的报文是否有效。
     * 
     * @param src
     * @return 若报文有效则返回true，否则返回false
     */
    private boolean checkMessage() {
        boolean isValid = false;
        Pattern pattern = Pattern.compile(ConfigData.Regex.parcel);
        Matcher matcher = pattern.matcher(lastReceivedMessage);

        // 校验信息基本格式
        if (!matcher.find()) {
            isValid = false;
        } else {

            // 由于约定包格式前4个字符必须为形如0x01格式的字串
            // 因此CRC校验符位置小于4的一定为包损坏
            // 由于当无法找到CRC校验符时返回的是-1，因此此处一并将这种情况处理了
            int crcPosition = lastReceivedMessage.indexOf("_CRC32:");
            if (crcPosition >= 4) {
                String crcValue = lastReceivedMessage
                        .substring(crcPosition + 7);

                String rawData = lastReceivedMessage.substring(0, crcPosition);
                if (crcValue.equals(Tools.getCRC32(rawData))) {
                    isValid = true;
                } else {
                    isValid = false;
                }
            }
        }
        return isValid;
    }

    /**
     * 取得任务类型。由于Java 1.7版本以下不支持String类型对比，此函数会将任务类型转换为int类型。
     * 
     * @return 转化为int的任务类型
     */
    private final int getTaskType() {
        return Integer.parseInt(lastReceivedMessage.substring(2, 4));
    }

    /**
     * 取得报文中包含的信息列表
     * 
     * @return
     */
    private String[] getMessageList() {
        int crcPosition = lastReceivedMessage.indexOf("_CRC32:");
        String messageContainer = lastReceivedMessage.substring(5, crcPosition);

        return messageContainer.split("_");
    }

}
