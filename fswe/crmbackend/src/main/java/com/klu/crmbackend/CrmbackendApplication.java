package com.klu.crmbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class CrmbackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(CrmbackendApplication.class, args);
	}

}
