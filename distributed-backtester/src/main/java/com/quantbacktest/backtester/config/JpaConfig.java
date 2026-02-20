package com.quantbacktest.backtester.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA configuration for database access and entity management.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.quantbacktest.backtester.repository")
@EnableJpaAuditing
@EnableTransactionManagement
public class JpaConfig {
    // Additional JPA configuration can be added here if needed
}
