package com.ming.imchatserver;

import java.sql.Driver;
import java.sql.DriverManager;

/**
 * test - 代码模块说明。
 * <p>
 * 本类注释由统一规范补充，描述职责、边界与使用语义。
 */
    public class test {
    /**
     * 方法说明。
     */
        public static void main(String[] args) {
            try {
                // 加载驱动（MySQL 8.x 可省略，自动加载）
                Class.forName("com.mysql.cj.jdbc.Driver");
                System.out.println("MySQL 驱动加载成功！");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                System.out.println("驱动加载失败，请检查依赖！");
            }
        }
    }

