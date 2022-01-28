package com.yue.ddns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

    private Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    @Autowired
    private WakeOnLan wakeOnLan;

    @RequestMapping("/wol")
    public String wol(String mac, String ip) {
        if (StringUtils.hasText(mac)) {
            String[] macArray = mac.split("-");
            char[] macChars = new char[6];
            for (int i = 0; i < macArray.length; i++) {
                int mac_part = Integer.parseInt(macArray[i], 16);
                macChars[i] = (char) mac_part;
            }
            wakeOnLan.send(macChars, ip);
        } else {
            wakeOnLan.send(null, ip);
        }
        return "success";
    }
}
