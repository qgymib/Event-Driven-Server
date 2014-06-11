package com.qgymib.findthetoiletserver;

import com.qgymib.findthetoiletserver.data.Database;

final public class ConfigData {

    public static final class AIO{
        public static final int SEND_BLOCK = 4096;
        public static final int RCV_BLOCK_SIZE = 4096;
    }
    
    /**
     * 数据库连接设置
     * 
     * @author qgymib
     *
     */
    public static final class DataBase {
        /**
         * 数据库连接地址
         */
        public static final String url = "jdbc:mysql://localhost:3306/find_the_toilet";
        /**
         * 数据库用户名
         */
        public static final String username = "root";
        /**
         * 数据库密码
         */
        public static final String password = "";

        private static Database database = null;

        public static Database getDatabase() {
            if (database == null) {
                try {
                    database = new Database(url, username, password);
                } catch (ClassNotFoundException e) {
                    System.err.println("Database Load Failed");
                }
            }
            return database;
        }
    }

    /**
     * 线程池设置
     * 
     * @author qgymib
     *
     */
    public static final class ThreadPool {
        /**
         * 线程池大小
         */
        public static final int thread_pool_size = 100;
    }

    /**
     * 网络相关参数
     * 
     * @author qgymib
     *
     */
    public static final class Net {
        /**
         * 服务器监听端口
         */
        public static final int server_port = 9876;
        /**
         * 数据包请求重新发送的最大次数
         */
        public static final int connect_reset_count = 10;
        /**
         * 连接超时设置
         */
        public static final int connect_timeout = 10 * 1000;
        /**
         * 网络连接编码格式
         */
        public static final String encoding = "UTF-8";
    }

    /**
     * 自定义协议类型
     * 
     * @author qgymib
     *
     */
    public static final class MessageType {
        /**
         * 交互已完成，可以关闭连接
         */
        public static final int FIN = 0x00;
        /**
         * 搜索周边洗手间信息
         */
        public static final int SEARCH = 0x01;
        /**
         * 修正洗手间地点信息
         */
        public static final int FIX = 0x02;
        /**
         * 请求增加洗手间地点
         */
        public static final int INSERT = 0x03;
        /**
         * 请求删除洗手间信息
         */
        public static final int DELETE = 0x04;
        /**
         * 请求注册用户
         */
        public static final int SIGNUP = 0x05;
        /**
         * 请求验证用户
         */
        public static final int LOGIN = 0x06;
        /**
         * 数据包损坏或丢失，请求重新发送数据包
         */
        public static final int LOST = 0x07;
        /**
         * 搜索结果-数据版本
         */
        public static final int SEARCH_VERSION = 0x08;
        /**
         * 搜索结果-数据信息
         */
        public static final int SEARCH_VALUE = 0x09;
    }

    /**
     * 用户有关信息的反馈约定
     * 
     * @author qgymib
     *
     */
    public static final class Account {
        /**
         * 用户权限列表。所有数值均大于零以表示权限。
         * 
         * @author qgymib
         *
         */
        public static final class Permission {
            /**
             * 正常权限（普通用户）
             */
            public static final int normal = 0;
            /**
             * 高级权限（管理员用户）
             */
            public static final int admin = 1;
            /**
             * 完全控制（开发者）
             */
            public static final int developer = 2;
        }

        /**
         * 登录/注册错误代码。所有数值均小于零。
         * 
         * @author qgymib
         *
         */
        public static final class Errno {
            /**
             * 用户名不存在
             */
            public static final int username_invalid = -1;
            /**
             * 密码错误
             */
            public static final int passwd_invalid = -2;
            /**
             * 网络连接异常
             */
            public static final int connection_error = -3;
            /**
             * 由于未知原因导致的验证失败
             */
            public static final int unknow = -4;
            /**
             * 用户名已存在
             */
            public static final int username_taken = -5;
        }

    }

    /**
     * 正则表达式校验字符串库。
     * 
     * @author qgymib
     *
     */
    public static final class Regex {
        /**
         * 校验报文。
         */
        public static final String parcel = "^0x0[0-9][\\d\\w_\\-]*_CRC32:[a-fA-F\\d]{1,}$";
    }
}
