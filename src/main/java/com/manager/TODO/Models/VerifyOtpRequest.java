package com.manager.TODO.Models;

import lombok.Data;

@Data
public class VerifyOtpRequest {
    private String email;
    private String otp;
}