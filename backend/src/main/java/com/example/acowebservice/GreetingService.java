package com.example.acowebservice;

import org.springframework.stereotype.Service;

@Service
public class GreetingService {
	public String getGreeting(String languageCode) {
        if ("vn".equalsIgnoreCase(languageCode)) {
            return "Xin chào!";
        } else if ("en".equalsIgnoreCase(languageCode)) {
            return "Hello!";
        } else {
            return "Greeting not found for language: " + languageCode;
        }
    }
}
