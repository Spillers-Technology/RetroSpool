package io.retrospool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RetrospoolApplication {

    public static void main(String[] args) {
        SpringApplication.run(RetrospoolApplication.class, args);
    }
}
