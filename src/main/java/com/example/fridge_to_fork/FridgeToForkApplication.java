package com.example.fridge_to_fork;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class FridgeToForkApplication {

	public static void main(String[] args) {
		SpringApplication.run(FridgeToForkApplication.class, args);
	}

}
