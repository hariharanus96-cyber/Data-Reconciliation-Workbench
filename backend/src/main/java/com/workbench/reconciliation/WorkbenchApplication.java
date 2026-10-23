package com.workbench.reconciliation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class WorkbenchApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkbenchApplication.class, args);
    }
}