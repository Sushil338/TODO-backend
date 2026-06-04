package com.manager.TODO.Models;

import lombok.Data;

@Data
public class LoginRequest {
    private String email;
    private String password;
}