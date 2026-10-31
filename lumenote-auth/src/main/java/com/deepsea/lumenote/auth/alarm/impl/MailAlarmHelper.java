package com.deepsea.lumenote.auth.alarm.impl;

import com.deepsea.lumenote.auth.alarm.AlarmInterface;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MailAlarmHelper implements AlarmInterface {

    /**
     * 发送告警信息
     */
    @Override
    public boolean send(String message) {
        log.info("==> 【邮件告警】：{}", message);

        // 业务逻辑...

        return true;
    }
}
