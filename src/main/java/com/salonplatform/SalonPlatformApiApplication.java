package com.salonplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SalonPlatformApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(SalonPlatformApiApplication.class, args);
	}

}
