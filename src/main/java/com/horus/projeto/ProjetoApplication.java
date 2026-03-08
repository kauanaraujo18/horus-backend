package com.horus.projeto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProjetoApplication {	

	public static void main(String[] args) {
		SpringApplication.run(ProjetoApplication.class, args);
		System.out.println("SENHA 123 CRIPTOGRAFADA: " + new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("1212"));
	}

}