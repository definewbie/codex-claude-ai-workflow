package dev.dual.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DualServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DualServerApplication.class, args);
    }
}
