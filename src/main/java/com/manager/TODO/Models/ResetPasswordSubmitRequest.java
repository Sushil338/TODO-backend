package com.manager.TODO.Models;

import lombok.Data;

@Data
public class ResetPasswordSubmitRequest {
    private String email;
    private String otp;
    private String newPassword;
}