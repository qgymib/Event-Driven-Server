package com.qgymib.findthetoiletserver.support;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;

/**
 * 常用工具库，封装一些通用操作。
 * 
 * @author qgymib
 *
 */
public class Tools {

    /**
     * 取得字符串的MD5校验码。
     * 
     * @param src
     *            需要取得md5码的字符串
     * @return 字符串的md5值
     */
    public static String getMD5(String src) {
        char hexDigits[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                'a', 'b', 'c', 'd', 'e', 'f' };// 用来将字节转换成16进制表示的字符
        String hexResult = null;
        MessageDigest md;

        try {
            md = MessageDigest.getInstance("MD5");
            md.update(src.getBytes());
            byte[] result = md.digest();
            // 每个字节用 16 进制表示的话，使用两个字符， 所以表示成 16
            char str[] = new char[16 * 2];
            for (int k = 0, i = 0; i < 16; i++) {
                // 从第一个字节开始，对 MD5 的每一个字节
                // 转换成16进制字符
                byte byte0 = result[i];// 取第 i 个字节
                // 取字节中高 4 位的数字转换,//
                // >>>
                // 为逻辑右移，将符号位一起右移
                str[k++] = hexDigits[byte0 >>> 4 & 0xf];
                // 取字节中低 4 位的数字转换
                str[k++] = hexDigits[byte0 & 0xf];
            }
            hexResult = new String(str);
        } catch (NoSuchAlgorithmException e) {
            
        }

        return hexResult;
    }

    /**
     * 取得指定字符串的CRC32校验值。
     * 
     * @param src
     *            需要取得CRC32校验码的字符串
     * @return 字符串的CRC32码
     */
    public static String getCRC32(String src) {
        String hexResult = null;
        CRC32 crc32 = new CRC32();

        crc32.update(src.getBytes());
        hexResult = Long.toHexString(crc32.getValue());

        return hexResult;
    }

}
