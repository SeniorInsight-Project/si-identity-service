package com.seniorinsight.identity;

import org.springframework.boot.SpringApplication;

/** Sobe o serviço localmente com PostgreSQL, RabbitMQ e Redis do Testcontainers: {@code mvn spring-boot:test-run}. */
public class TestIdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(IdentityServiceApplication::main).with(ContainersConfig.class).run(args);
    }
}
