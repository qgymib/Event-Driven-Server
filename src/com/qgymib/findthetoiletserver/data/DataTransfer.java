package com.qgymib.findthetoiletserver.data;

/**
 * 数据传输封装类
 * 
 * @author qgymib
 *
 */
public class DataTransfer {

    /**
     * 异步读取socket回调函数
     * 
     * @author qgymib
     *
     */
    public static interface SocketReadDataTransfer {
        /**
         * 在回调函数中处理异步得到的信息
         * 
         * @param data
         */
        public void dataTransfer(String data);
    }

    /**
     * 由数据库封装的城市洗手间信息集。
     * 
     * @author qgymib
     *
     */
    public static final class ToiletSetInfo {
        /**
         * 信息版本
         */
        public int version;
        /**
         * 信息内容
         */
        public String value;
    }
}
