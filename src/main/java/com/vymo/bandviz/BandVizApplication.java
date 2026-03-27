package com.vymo.bandviz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BandVizApplication {

    public static void main(String[] args) {
        SpringApplication.run(BandVizApplication.class, args);
    }
}
