package com.example.bruteforce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling // @Scheduled 만료 키 정리 활성화
public class BruteForceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BruteForceApplication.class, args);
    }
}
