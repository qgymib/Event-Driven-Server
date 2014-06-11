package com.qgymib.findthetoiletserver.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.qgymib.findthetoiletserver.ConfigData;
import com.qgymib.findthetoiletserver.data.DataTransfer.ToiletSetInfo;


public class Database {
    private Connection conn;
    private Statement smt;
    private String path;
    private String username;
    private String password;

    /**
     * FTTDataBase用于连接指定url的数据库
     * 
     * @throws ClassNotFoundException
     */
    public Database() throws ClassNotFoundException {
        this.conn = null;
        setUrl(null);
        setUsername(null);
        setPassword(null);
        loadDriver();
    }

    /**
     * 带连接的FTTDataBase 数据库地址设置为url，用户名设置为root，密码为空
     * 
     * @param url
     * @throws ClassNotFoundException
     */
    public Database(String url) throws ClassNotFoundException {
        this.conn = null;
        setUrl(url);
        setUsername("root");
        setPassword("");
        loadDriver();
    }

    /**
     * 完全可控的FTTDataBase
     * 
     * @param url
     * @param username
     * @param password
     * @throws ClassNotFoundException
     */
    public Database(String url, String username, String password)
            throws ClassNotFoundException {
        this.conn = null;
        setUrl(url);
        setUsername(username);
        setPassword(password);
        loadDriver();
    }

    /**
     * 加载jdbc驱动
     * 
     * @throws ClassNotFoundException
     */
    private void loadDriver() throws ClassNotFoundException {
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw e;
        }
    }

    /**
     * 取得用于连接数据库的用户名
     * 
     * @return
     */
    public String getUsername() {
        return username;
    }

    /**
     * 设置用户连接数据库的用户名
     * 
     * @param username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 取得用于连接数据库的密码
     * 
     * @param username
     */
    public String getPassword() {
        return password;
    }

    /**
     * 设置用于连接数据库的密码
     * 
     * @param password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 设置远程数据库地址
     * 
     * @param url
     */
    public void setUrl(String url) {
        this.path = url;
    }

    /**
     * 取得远程数据库地址
     * 
     * @return
     */
    public String getUrl() {
        return path;
    }

    /**
     * 连接远程数据库
     * 
     * @throws SQLException
     */
    public void connect() throws SQLException {
        try {
            this.conn = DriverManager.getConnection(path, username, password);

            this.smt = conn.createStatement();

        } catch (SQLException e) {
            System.err.println("fail to connect database");
            throw e;
        }
    }

    /**
     * 关闭远程数据库连接
     */
    public void disconnect() {
        try {
            if (this.smt != null) {
                this.smt.close();
            }
            if (this.conn != null) {
                this.conn.close();
            }

        } catch (SQLException e) {
            System.err.println(e.getMessage());
        } finally {
            this.smt = null;
            this.conn = null;
        }
    }

    /**
     * 登录模块。根据用户名和密码返回用户权限或错误代码。
     * 
     * @param username
     *            用户名
     * @param passwd_md5
     *            经md5加密的密码
     * @return 用户权限或错误代码
     * @see ConfigData.Account.Permission
     * @see ConfigData.Account.Errno
     */
    public int loginTask(String username, String passwd_md5) {
        int result = ConfigData.Account.Errno.unknow;

        String sql_vaild_user = "SELECT permission FROM `account_info`"
                + "WHERE username = '" + username + "' AND PASSWORD = '"
                + passwd_md5 + "'";
        String sql_vaild_username = "SELECT username FROM `account_info`"
                + "WHERE username = '" + username + "'";
        ResultSet rs = null;
        try {
            rs = smt.executeQuery(sql_vaild_user);

            if (rs.next()) {
                // 若返回集包含元素，则存在此账户，返回账户权限
                result = rs.getInt("permission");
            } else {
                // 用户名或密码错误
                rs = smt.executeQuery(sql_vaild_username);
                if (rs.next()) {
                    // 若存在用户名，则为用户输入密码错误
                    result = ConfigData.Account.Errno.passwd_invalid;
                } else {
                    // 否则为用户名不存在
                    result = ConfigData.Account.Errno.username_invalid;
                }
            }
        } catch (SQLException e) {
            System.err.println("login task 执行失败");
            e.printStackTrace();
        }

        return result;
    }

    /**
     * 注册模块。将给出的用户名和密码加入到数据库中。权限默认为normal
     * 
     * @param username
     *            用户名
     * @param passwd_md5
     *            经md5加密的密码
     * @return 权限或错误码
     * @see ConfigData.Account.Permission
     * @see ConfigData.Account.Errno
     */
    public int signupTask(String username, String passwd_md5) {
        return signupTask(username, passwd_md5,
                ConfigData.Account.Permission.normal);
    }

    /**
     * 注册模块。将给出的用户名、密码和权限加入到数据库中。
     * 
     * @param username
     *            用户名
     * @param passwd_md5
     *            经md5加密的密码
     * @param permission
     *            用户权限
     * @return 用户权限或错误代码
     */
    public int signupTask(String username, String passwd_md5, int permission) {
        int result = ConfigData.Account.Errno.unknow;

        // 校验permission是否在权限列表中
        switch (permission) {
        case ConfigData.Account.Permission.normal:
        case ConfigData.Account.Permission.admin:
        case ConfigData.Account.Permission.developer:
            break;

        default:
            return result;
        }

        String sql_vaild_username = "SELECT username FROM `account_info`"
                + "WHERE username = '" + username + "'";
        String sql_insert_account = "INSERT INTO `find_the_toilet`.`account_info` "
                + "(`id` ,`username` ,`password` ,`permission`)"
                + "VALUES (NULL , '"
                + username
                + "', '"
                + passwd_md5
                + "', '"
                + permission + "');";

        ResultSet rs = null;

        try {
            rs = smt.executeQuery(sql_vaild_username);
            if (rs.next()) {
                // 用户名已存在
                result = ConfigData.Account.Errno.username_taken;
            } else {
                // 用户名不存在
                smt.executeUpdate(sql_insert_account);
                result = permission;
            }

        } catch (SQLException e) {
            System.err.println("signup task 执行失败");
            e.printStackTrace();
        }

        return result;
    }

    /**
     * 搜索模块。根据客户端所在城市返回城市中所有洗手间列表
     * 
     * @param locationKey
     * @return 洗手间列表<br/>
     *         null - 若信息不存在或出现错误
     */
    public ToiletSetInfo searchTask(String locationKey) {
        ToiletSetInfo result = new ToiletSetInfo();
        String sql_search = "SELECT version, value FROM `location` WHERE location_key = '"
                + locationKey + "'";
        ResultSet rs;

        try {
            rs = smt.executeQuery(sql_search);

            if (rs.next()) {
                result.version = rs.getInt(1);
                result.value = rs.getString(2);
            } else {
                result = null;
            }

        } catch (SQLException e) {
            System.err.println("search task 执行失败");
            e.printStackTrace();
            result = null;
        }

        return result;
    }
}
