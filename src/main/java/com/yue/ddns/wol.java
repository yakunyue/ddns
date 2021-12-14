package com.yue.ddns;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class wol {
    /**
     * main方法，发送UDP广播，实现远程开机，目标计算机的MAC地址为：28D2443568A7
     */
    public static void main(String[] args) {
        char[] mac = {0x50, 0x2F, 0x9B, 0x28, 0x17, 0x0C};
        send(mac);
    }

   

    private static void send(char[] mac){
        char[] head = { 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF };

        char[] cmd = new char[102];
        // 拼接数据包
        System.arraycopy(head, 0, cmd, 0, head.length);
        // 需要16遍重复目标mac
        for (int i = 0; i < 16; i++) {
            System.arraycopy(mac, 0, cmd, 6 + i * 6, head.length);
        }
        final String cmdStr = new String(cmd);
        try {
            byte[] data = cmdStr.getBytes("ISO-8859-1");
            /* 在Java UDP中单播与广播的代码是相同的，要实现具有广播功能的程序只需要使用广播地址即可 */
            InetAddress inetAddr = InetAddress.getByName("192.168.2.105");
            int port = 7010;
            // 获取广播socket
            // MulticastSocket client = new MulticastSocket(port);
            DatagramSocket client = new DatagramSocket();
            // 封装数据包
            DatagramPacket packet = new DatagramPacket(data, data.length, inetAddr, port);
            // 发送魔法包
            for (int i = 0; i < 3; i++) {
                System.out.println("开始发送魔包");
                client.send(packet);
                System.out.println("发送魔包完成");
                Thread.sleep(100);
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }
}