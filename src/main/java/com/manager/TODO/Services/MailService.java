package com.manager.TODO.Services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class MailService {

    @Value("${resend.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final String RESEND_API_URL = "https://api.resend.com/emails";

    public void sendOtpEmail(String toEmail, String otp) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("from", "TaskManager <onboarding@resend.dev>"); // Replace with your domain later
            body.put("to", new String[]{toEmail});
            body.put("subject", "Verify Your Account - OTP");
            body.put("html", "<h3>Welcome to TaskManager!</h3><p>Your verification code is: <strong>" + otp + "</strong></p><p>This OTP expires in 15 minutes.</p>");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(RESEND_API_URL, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to send email via Resend: " + response.getBody());
            }
        } catch (Exception e) {
            // Log this in production
            System.err.println("Error sending email: " + e.getMessage());
            throw new RuntimeException("Email delivery failed");
        }
    }
}