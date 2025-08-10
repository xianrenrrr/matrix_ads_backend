package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DemoApplication {

  @Value("${NAME:World}")
  String name;

  // Removed HelloworldController to avoid ambiguous mapping with HealthController
  // Root endpoint now handled by HealthController

  public static void main(String[] args) {
    SpringApplication.run(DemoApplication.class, args);
  }
}
