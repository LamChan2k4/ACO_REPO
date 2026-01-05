package com.example.acowebservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {
	private final GreetingService greetingService;
	@Autowired
	public GreetingController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }
	@GetMapping("/greeting/{lang}")
	public String handleGreetingRequest(@PathVariable String lang) {
		return greetingService.getGreeting(lang); 
	}
}
