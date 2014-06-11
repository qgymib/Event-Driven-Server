package com.qgymib.findthetoiletserver.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.sql.SQLException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.qgymib.findthetoiletserver.ConfigData;
import com.qgymib.findthetoiletserver.data.DataTransfer.ToiletSetInfo;
import com.qgymib.findthetoiletserver.data.Database;
import com.qgymib.findthetoiletserver.support.AIO;
import com.qgymib.findthetoiletserver.support.Tools;

public class NetService implements Runnable {

    private Selector selector = null;
    private ServerSocketChannel ssc = null;
    private Database db = ConfigData.DataBase.getDatabase();
    /**
     * 可读取缓存池
     */
    private static Hashtable<SelectionKey, ByteBuffer> readBuffers = new Hashtable<SelectionKey, ByteBuffer>();
    /**
     * 可写入缓存池
     */
    private static Hashtable<SelectionKey, String[]> writebuffers = new Hashtable<SelectionKey, String[]>();
    private boolean isRunnable = true;

    public NetService() {
        try {
            db.connect();
        } catch (SQLException e) {
            System.err.println("data base connect failed");
            isRunnable = false;
        }
    }

    @Override
    public void run() {
        if (!isRunnable) {
            return;
        }
        try {
            selector = Selector.open();
            ServerSocketChannel ssc = ServerSocketChannel.open();
            // 绑定端口
            ssc.socket()
                    .bind(new InetSocketAddress(ConfigData.Net.server_port));
            // 设置非阻塞
            ssc.configureBlocking(false);
            SelectionKey sk = ssc.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("listen on port" + ConfigData.Net.server_port);

            while (true) {
                selector.select();

                System.out.println("event received");

                Iterator<SelectionKey> keyIterator = selector.selectedKeys()
                        .iterator();

                while (keyIterator.hasNext()) {
                    sk = (SelectionKey) keyIterator.next();

                    System.out.println("remove iterator");
                    keyIterator.remove();

                    if (sk.isValid()) {
                        handle(sk);
                    } else {
                        sk.cancel();
                    }

                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (selector != null) {
                    selector.close();
                }
                if (ssc != null) {
                    ssc.close();
                }
            } catch (IOException e) {

            } finally {
                ssc = null;
                selector = null;
            }
        }

    }

    private void handle(SelectionKey key) throws IOException {
        SocketChannel socketChannel;

        if (key.isConnectable()) {
            ((SocketChannel) key.channel()).finishConnect();
        } else if (key.isAcceptable()) {
            ServerSocketChannel channel = (ServerSocketChannel) key.channel();
            socketChannel = channel.accept();
            socketChannel.configureBlocking(false);
            socketChannel.socket().setTcpNoDelay(true);
            socketChannel.register(selector, SelectionKey.OP_READ);
        } else if (key.isReadable()) {
            socketChannel = (SocketChannel) key.channel();

            int count = 0;
            ByteBuffer receivebuffer = readBuffers.get(key);
            if (receivebuffer == null) {
                receivebuffer = ByteBuffer
                        .allocate(ConfigData.AIO.RCV_BLOCK_SIZE);
                readBuffers.put(key, receivebuffer);
            }
            String receivedMessage = "";
            if ((count = socketChannel.read(receivebuffer)) != -1) {
                receivebuffer.clear();
                receivedMessage = new String(receivebuffer.array(), 0,
                        count - 1);
            } else {
                // 当无法读取数据时，说明此时客户端连接无效，可以从监听列表中删除
                receivedMessage = null;
                socketChannel.close();
                key.cancel();
                readBuffers.remove(key);
                writebuffers.remove(key);
            }
            System.out.println("RcvData: " + receivedMessage);

            if (receivedMessage != null) {
                if (checkMessage(receivedMessage)) {
                    // 如果报文校验通过
                    System.out.println("报文校验通过");
                    handleClient(key, receivedMessage);
                } else {
                    // 若报文校验不通过
                    System.out.println("报文校验不通过");
                    writebuffers
                            .put(key,
                                    new String[] { packageMessage(ConfigData.MessageType.LOST) });
                    key.interestOps(SelectionKey.OP_WRITE);
                }
            }

        } else if (key.isWritable()) {
            socketChannel = (SocketChannel) key.channel();
            AIO.writeSocket(socketChannel, writebuffers.get(key));
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    /**
     * 客户端请求处理
     * 
     * @param key
     * @param rcvMsg
     */
    private void handleClient(SelectionKey key, String rcvMsg) {
        switch (getTaskType(rcvMsg)) {
        // 用户请求断开连接
        case ConfigData.MessageType.FIN:
            /*
             * 客户端发送结束请求之后，服务器告知客户端可以关闭请求。
             * 当客户端关闭连接之后，对应于客户端的key将不被阻塞，但是尝试读取信息时将返回-1
             */
            System.out.println("FIN");
            writebuffers
                    .put(key,
                            new String[] { packageMessage(ConfigData.MessageType.FIN) });
            break;

        // 搜索请求
        case ConfigData.MessageType.SEARCH:
            actionForSearch(key, rcvMsg);
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
            actionForSignup(key, rcvMsg);
            break;

        // 登录请求
        case ConfigData.MessageType.LOGIN:
            System.out.println("LOGIN");
            actionForLogin(key, rcvMsg);
            break;

        // 报文损坏，请求重新发送报文
        case ConfigData.MessageType.LOST:
            System.out.println("LOST");
            // 当请求重新发送报文时，由于对应于此客户端的发送缓冲区并没有更新，因此只需将key标为可写
            break;
        }

        key.interestOps(SelectionKey.OP_WRITE);
    }

    /**
     * 搜索任务
     * 
     * @param key
     * @param message
     */
    private void actionForSearch(SelectionKey key, String message) {
        ToiletSetInfo info = db.searchTask(getMessageList(message)[0]);
        String[] messageList;
        if (info == null) {
            messageList = new String[] {
                    packageMessage(ConfigData.MessageType.SEARCH_VERSION, "-1"),
                    packageMessage(ConfigData.MessageType.SEARCH_VALUE, "") };

        } else {
            messageList = new String[] {
                    packageMessage(ConfigData.MessageType.SEARCH_VERSION, ""
                            + info.version),
                    packageMessage(ConfigData.MessageType.SEARCH_VALUE,
                            info.value) };
        }
        writebuffers.put(key, messageList);
    }

    /**
     * 用户注册事件处理。报文为如下格式：
     * 0x05_qgymib_e10adc3949ba59abbe56e057f20f883e_CRC32:1278060152
     */
    private void actionForSignup(SelectionKey key, String message) {
        String[] messageList = getMessageList(message);
        String username = messageList[0];
        String passwd_md5 = messageList[1];

        int result = db.signupTask(username, passwd_md5);
        writebuffers.put(
                key,
                new String[] { packageMessage(ConfigData.MessageType.SIGNUP,
                        String.valueOf(result)) });
    }

    /**
     * 用户登录事件处理。报文为如下格式：
     * 0x06_qgymib_e10adc3949ba59abbe56e057f20f883e_CRC32:1278060152
     */
    private void actionForLogin(SelectionKey key, String message) {
        String[] messageList = getMessageList(message);
        String username = messageList[0];
        String passwd_md5 = messageList[1];

        int result = db.loginTask(username, passwd_md5);
        writebuffers.put(
                key,
                new String[] { packageMessage(ConfigData.MessageType.LOGIN,
                        String.valueOf(result)) });
    }

    /**
     * 校验报文
     * 
     * @param message
     * @return
     */
    private boolean checkMessage(String message) {
        boolean isValid = false;
        Pattern pattern = Pattern.compile(ConfigData.Regex.parcel);
        Matcher matcher = pattern.matcher(message);

        // 校验信息基本格式
        if (!matcher.find()) {
            isValid = false;
        } else {

            // 由于约定包格式前4个字符必须为形如0x01格式的字串
            // 因此CRC校验符位置小于4的一定为包损坏
            // 由于当无法找到CRC校验符时返回的是-1，因此此处一并将这种情况处理了
            int crcPosition = message.indexOf("_CRC32:");
            if (crcPosition >= 4) {
                String crcValue = message.substring(crcPosition + 7);

                String rawData = message.substring(0, crcPosition);
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
     * 包裹报文
     * 
     * @param type
     * @param messageList
     * @return
     */
    private String packageMessage(int type, String... messageList) {
        String packagedMessage = null;
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
        packagedMessage = message + "CRC32:"
                + Tools.getCRC32(message.substring(0, message.length() - 1));

        return packagedMessage;
    }

    /**
     * 取得任务类型。由于Java 1.7版本以下不支持String类型对比，此函数会将任务类型转换为int类型。
     * 
     * @return 转化为int的任务类型
     */
    private final int getTaskType(String message) {
        return Integer.parseInt(message.substring(2, 4));
    }

    /**
     * 取得报文中包含的信息列表
     * 
     * @return
     */
    private String[] getMessageList(String message) {
        int crcPosition = message.indexOf("_CRC32:");
        String messageContainer = message.substring(5, crcPosition);

        return messageContainer.split("_");
    }
}
