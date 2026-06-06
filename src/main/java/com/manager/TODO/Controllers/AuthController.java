package com.manager.TODO.Controllers;

import com.manager.TODO.Models.LoginRequest;
import com.manager.TODO.Models.RegisterUserRequest;
import com.manager.TODO.Models.User;
import com.manager.TODO.Repository.UserRepository;
import com.manager.TODO.Services.JwtService;
import com.manager.TODO.Services.MailService;
import com.manager.TODO.Services.OtpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private OtpService otpService;

    @Autowired
    private MailService mailService;

    @GetMapping("/test")
    public String test() {
        return "Working";
    }

    private Map<String, Object> buildUserResponse(User user) {
        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put("id", user.getId());
        userResponse.put("username", user.getUsername());
        userResponse.put("email", user.getEmail());
        return userResponse;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterUserRequest request) { // Updated parameter
        try{
        // 1. Validate incoming data
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Error: Username is required!");
        }
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Error: Email is required!");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Error: Password is required!");
        }

        // 2. Check uniqueness
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Error: Username is already taken!");
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Error: Email is already registered!");
        }

        // Pre-encrypt password so we store it securely in Redis cache
        request.setPassword(encoder.encode(request.getPassword()));

        // 3. Generate OTP and cache registration data in Redis
        String otp = otpService.generateAndSaveOtp(request.getEmail(), request);

        // 4. Dispatch verification mail via Resend
        mailService.sendOtpEmail(request.getEmail(), otp);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "OTP sent to your email. Please verify to complete registration.");
        response.put("email", request.getEmail());
        return ResponseEntity.ok(response);}
        catch (Exception e){
            // This forces the explicit Java crash to print clearly in your Docker log window
            System.err.println("=== REGISTRATION ERROR DETECTED ===");
            e.printStackTrace();
            System.err.println("====================================");

            return ResponseEntity.status(500).body("Error details: " + e.getMessage());
        }
    }


    @PostMapping("/verify-register-otp")
    public ResponseEntity<?> verifyRegisterOtp(@RequestBody com.manager.TODO.Models.VerifyOtpRequest request) {
        String savedOtp = otpService.getSavedOtp(request.getEmail());

        if (savedOtp == null) {
            return ResponseEntity.badRequest().body("Error: OTP expired or invalid registration session.");
        }

        if (!savedOtp.equals(request.getOtp())) {
            return ResponseEntity.badRequest().body("Error: Incorrect OTP code.");
        }

        // Fetch temporary data out of Redis cache
        RegisterUserRequest pendingUser = otpService.getPendingRegistration(request.getEmail());
        if (pendingUser == null) {
            return ResponseEntity.badRequest().body("Error: Registration session timed out.");
        }

        // Map to MySQL User Object
        User newUser = new User();
        newUser.setUsername(pendingUser.getUsername());
        newUser.setEmail(pendingUser.getEmail());
        newUser.setPassword(pendingUser.getPassword()); // already encrypted

        // Persist to DB
        User savedUser = userRepository.save(newUser);

        // Cleanup Redis session keys
        otpService.clearOtpAndRegistration(request.getEmail());

        // Generate auto-login JWT token
        String token = jwtService.generateToken(savedUser.getUsername());

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Account verified and registered successfully!");
        response.put("token", token);
        response.put("user", buildUserResponse(savedUser));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest request) {
        try {
            // 1. Find the user by email first, since the frontend submits an email
            User dbUser = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new AuthenticationException("Invalid email or password") {});

            // 2. Pass the resolved username and password to the AuthenticationManager
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dbUser.getUsername(), request.getPassword()));

            if (authentication.isAuthenticated()) {
                String token = jwtService.generateToken(dbUser.getUsername());

                Map<String, Object> response = new HashMap<>();
                response.put("token", token);
                response.put("user", buildUserResponse(dbUser));

                return ResponseEntity.ok(response);
            }
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication failed");
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Principal principal) {
        if (principal == null) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "Unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        User user = userRepository.findByUsername(principal.getName()).orElse(null);

        if (user == null) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "User not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        return ResponseEntity.ok(buildUserResponse(user));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody com.manager.TODO.Models.ForgotPasswordRequest request) {
        // 1. Verify if the email actually exists in your MySQL DB
        java.util.Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
        if (userOpt.isEmpty()) {
            // Security Best Practice: Don't explicitly reveal if an email doesn't exist.
            // Just say "If the email exists, an OTP has been sent."
            return ResponseEntity.ok("If the account exists, an OTP has been sent to your email.");
        }

        // 2. Generate Reset OTP and save to Redis
        String otp = otpService.generateAndSaveResetOtp(request.getEmail());

        // 3. Send email via Resend
        mailService.sendOtpEmail(request.getEmail(), otp);
        // Note: You can customize mailService to say "Reset Password OTP" instead of "Registration" if you prefer!

        return ResponseEntity.ok("OTP sent to your email for password resetting.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody com.manager.TODO.Models.ResetPasswordSubmitRequest request) {
        // 1. Validate inputs
        if (request.getNewPassword() == null || request.getNewPassword().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Error: New password is required.");
        }

        // 2. Fetch OTP from Redis
        String savedOtp = otpService.getResetOtp(request.getEmail());
        if (savedOtp == null) {
            return ResponseEntity.badRequest().body("Error: OTP expired or invalid session.");
        }

        // 3. Verify OTP matches
        if (!savedOtp.equals(request.getOtp())) {
            return ResponseEntity.badRequest().body("Error: Incorrect OTP code.");
        }

        // 4. Update password in MySQL database
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found during password reset step."));

        user.setPassword(encoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // 5. Clean up Redis OTP
        otpService.clearResetOtp(request.getEmail());

        return ResponseEntity.ok("Password reset successfully! You can now log in with your new password.");
    }
}