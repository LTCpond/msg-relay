package com.ltcpond.msgrelay;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "com.ltcpond.msgrelay")
@MapperScan("com.ltcpond.msgrelay.**.repository")
@EnableCaching
@EnableScheduling
@EnableAsync
public class MsgRelayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsgRelayApplication.class, args);
    }

}
