package com.campuslink;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.campuslink.mapper")
@EnableScheduling
public class CampusLinkApplication {
    public static void main(String[] args) { SpringApplication.run(CampusLinkApplication.class, args); }
}
