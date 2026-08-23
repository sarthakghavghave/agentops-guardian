package com.agentops.guardian;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AgentOpsGuardianApplication {

	public static void main(String[] args) {
		SpringApplication.run(AgentOpsGuardianApplication.class, args);
		System.out.println("App started");
	}
}
