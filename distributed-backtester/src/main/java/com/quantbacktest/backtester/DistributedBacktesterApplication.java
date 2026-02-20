package com.quantbacktest.backtester;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Distributed Backtester service.
 * This is a monolithic backend service with internal background workers.
 */
@SpringBootApplication
public class DistributedBacktesterApplication {

    public static void main(String[] args) {
        SpringApplication.run(DistributedBacktesterApplication.class, args);
    }

}
