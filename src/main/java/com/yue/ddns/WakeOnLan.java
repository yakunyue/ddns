package com.yue.ddns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

@Service
public class WakeOnLan {

    private Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private static char[] b550_MAC = {0x50, 0x2F, 0x9B, 0x28, 0x17, 0x0C};

    /**
     * 发送UDP广播，实现远程开机，默认取 b550 的MAC地址：50-2F-9B-28-17-0C
     */
    public void send(char[] mac, String ip) {
        if (mac == null || mac.length <= 0) {
            mac = b550_MAC;
        }
        char[] head = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};

        char[] cmd = new char[102];
        // 拼接数据包
        System.arraycopy(head, 0, cmd, 0, head.length);
        // 需要16遍重复目标mac
        for (int i = 0; i < 16; i++) {
            System.arraycopy(mac, 0, cmd, 6 + i * 6, head.length);
        }
        final String cmdStr = new String(cmd);
        try {
            byte[] data = cmdStr.getBytes(StandardCharsets.ISO_8859_1);
            /* 在Java UDP中单播与广播的代码是相同的，要实现具有广播功能的程序只需要使用广播地址即可 */
            InetAddress inetAddr = StringUtils.hasText(ip) ? InetAddress.getByName(ip) : InetAddress.getByName("192.168.2.105");
            int port = 7010;
            // 获取广播socket
            // MulticastSocket client = new MulticastSocket(port);
            DatagramSocket client = new DatagramSocket();
            // 封装数据包
            DatagramPacket packet = new DatagramPacket(data, data.length, inetAddr, port);
            // 发送魔法包
            for (int i = 0; i < 3; i++) {
                logger.info("开始发送魔包");
                client.send(packet);
                logger.info("发送魔包完成");
                Thread.sleep(100);
            }
        } catch (Exception e) {
            logger.error("发送魔包失败，errorMessage:{}", e.getMessage());
            e.printStackTrace();
        }
    }
}