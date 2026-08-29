package com.ltcpond.msgrelay;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.ltcpond.msgrelay")
@MapperScan("com.ltcpond.msgrelay.**.repository")
@EnableCaching
@EnableScheduling
public class MsgRelayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsgRelayApplication.class, args);
    }

}
