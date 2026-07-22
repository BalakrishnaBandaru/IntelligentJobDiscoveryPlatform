package com.jobdiscovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JobDiscoveryPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobDiscoveryPlatformApplication.class, args);
	}

}
