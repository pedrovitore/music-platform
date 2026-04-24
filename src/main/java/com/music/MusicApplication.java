package com.music;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MusicApplication {
    public static void main(String[] args) {
        SpringApplication.run(MusicApplication.class, args);
        System.out.println("========================================");
        System.out.println("Music Platform Running!");
        System.out.println("Open: http://localhost:8080");
        System.out.println("========================================");
    }
}