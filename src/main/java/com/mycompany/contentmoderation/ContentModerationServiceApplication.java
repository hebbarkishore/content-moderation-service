package com.mycompany.contentmoderation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.aws.messaging.config.annotation.EnableSqs;

@SpringBootApplication
@EnableSqs
public class ContentModerationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentModerationServiceApplication.class, args);
    }

}
