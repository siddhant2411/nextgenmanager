package com.nextgenmanager.nextgenmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
public class NextgenmanagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(NextgenmanagerApplication.class, args);
	}

}
