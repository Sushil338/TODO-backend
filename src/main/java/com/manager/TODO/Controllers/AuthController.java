package com.manager.TODO.Controllers;

import com.manager.TODO.Models.LoginRequest;
import com.manager.TODO.Models.RegisterUserRequest; // Imported new DTO
import com.manager.TODO.Models.UpdateUserRequest;
import com.manager.TODO.Models.User;
import com.manager.TODO.Repository.UserRepository;
import com.manager.TODO.Services.JwtService;
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

        // 3. Map DTO data to User entity
        User newUser = new User();
        newUser.setUsername(request.getUsername());
        newUser.setEmail(request.getEmail());

        // Encrypt the password securely
        newUser.setPassword(encoder.encode(request.getPassword()));

        // 4. Save to Database
        User savedUser = userRepository.save(newUser);

        // 5. Generate Token for Automatic Login
        String token = jwtService.generateToken(savedUser.getUsername());

        // 6. Build response payload matching your frontend structure
        Map<String, Object> response = new HashMap<>();
        response.put("message", "User registered successfully!");
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

    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        User existingUser = userRepository.findById(id).orElse(null);

        if (existingUser == null) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "User not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        if (request.getUsername() != null && !request.getUsername().isEmpty()) {
            User existingByUsername = userRepository.findByUsername(request.getUsername()).orElse(null);

            if (existingByUsername != null && !existingByUsername.getId().equals(id)) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Username already taken");
                return ResponseEntity.badRequest().body(error);
            }
            existingUser.setUsername(request.getUsername());
        }

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            existingUser.setPassword(encoder.encode(request.getPassword()));
        }

        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            existingUser.setEmail(request.getEmail());
        }

        userRepository.save(existingUser);

        String newToken = jwtService.generateToken(existingUser.getUsername());

        Map<String, Object> response = new HashMap<>();
        response.put("message", "User updated successfully");
        response.put("user", buildUserResponse(existingUser));
        response.put("token", newToken);

        return ResponseEntity.ok(response);
    }
}