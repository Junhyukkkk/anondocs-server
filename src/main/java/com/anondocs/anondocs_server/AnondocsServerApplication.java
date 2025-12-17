package com.anondocs.anondocs_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class AnondocsServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(AnondocsServerApplication.class, args);
	}

}
