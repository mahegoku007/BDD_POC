package com.classroom.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Audit Service.
 * Records booking lifecycle events in MongoDB for audit/compliance.
 */
@SpringBootApplication
public class AuditServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}

