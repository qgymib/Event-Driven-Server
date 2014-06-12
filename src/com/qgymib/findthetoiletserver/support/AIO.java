package com.qgymib.findthetoiletserver.support;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.qgymib.findthetoiletserver.ConfigData;
import com.qgymib.findthetoiletserver.data.DataTransfer.SocketReadDataTransfer;

/**
 * 模拟异步IO
 * 
 * @author qgymib
 *
 */
public class AIO {

    private static ExecutorService threadpool = Executors.newFixedThreadPool(5);

    /**
     * 异步读取指定SocketChannel内容
     * 
     * @param sc
     *            SocketChannle
     * @param transfer
     *            用于处理读取内容的异步回调函数
     */
    public static void readSocket(SocketChannel sc,
            SocketReadDataTransfer transfer) {
        threadpool.execute(new ReadSocketTask(sc, transfer));
    }

    /**
     * 异步写入SocketChannel消息
     * 
     * @param sc
     * @param message
     */
    public static void writeSocket(SocketChannel sc, String[] messageList) {
        threadpool.execute(new WriteSocketTask(sc, messageList));
    }

    /**
     * 异步读取指定SocketChannel内容
     * 
     * @author qgymib
     *
     */
    private static class ReadSocketTask implements Runnable {

        private SocketChannel channel;
        private SocketReadDataTransfer transfer;
        private ByteBuffer receivebuffer = ByteBuffer
                .allocate(ConfigData.AIO.RCV_BLOCK_SIZE);
        private String receivedMessage = "";

        public ReadSocketTask(SocketChannel sc, SocketReadDataTransfer transfer) {
            this.channel = sc;
            this.transfer = transfer;
        }

        @Override
        public void run() {
            int count = 0;
            try {
                if ((count = channel.read(receivebuffer)) != -1) {
                    receivedMessage += new String(receivebuffer.array(), 0,
                            count - 1);
                    receivebuffer.clear();
                }

            } catch (IOException e) {
                receivedMessage = null;
            } finally {
                // 执行回调
                transfer.dataTransfer(receivedMessage);
            }
        }

    }

    /**
     * 异步写入指定SocketChannel数据
     * 
     * @author qgymib
     *
     */
    private static class WriteSocketTask implements Runnable {

        private SocketChannel socketChannel;
        private String[] messageList;
        private ByteBuffer sendbuffer = ByteBuffer
                .allocate(ConfigData.AIO.SEND_BLOCK);

        public WriteSocketTask(SocketChannel sc, String... messageList) {
            this.socketChannel = sc;
            this.messageList = messageList;
        }

        @Override
        public void run() {
            for (int i = 0; i < messageList.length; i++) {

                String sendMessage = messageList[i] + "\n";

                sendbuffer.clear();
                sendbuffer.put(sendMessage.getBytes(Charset.forName("UTF-8")));
                sendbuffer.flip();

                try {
                    socketChannel.write(sendbuffer);

                    sendbuffer.flip();
                    System.out.println("Send: " + sendMessage);
                } catch (IOException e) {
                }
            }
        }
    }
}
