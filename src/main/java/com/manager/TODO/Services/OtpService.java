package com.manager.TODO.Services;

import com.manager.TODO.Models.RegisterUserRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class OtpService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String OTP_PREFIX = "OTP:";
    private static final String REG_PREFIX = "REG_DATA:";
    private static final String RESET_PREFIX = "RESET_OTP:";

    public String generateAndSaveOtp(String email, RegisterUserRequest registrationData) {
        // Generate 6 digit OTP
        String otp = String.format("%06d", new Random().nextInt(999999));

        // Store OTP in Redis (Valid for 15 mins)
        redisTemplate.opsForValue().set(OTP_PREFIX + email, otp, 15, TimeUnit.MINUTES);

        // Store user registration details temporarily in Redis until verified
        redisTemplate.opsForValue().set(REG_PREFIX + email, registrationData, 15, TimeUnit.MINUTES);

        return otp;
    }

    public String getSavedOtp(String email) {
        return (String) redisTemplate.opsForValue().get(OTP_PREFIX + email);
    }

    public RegisterUserRequest getPendingRegistration(String email) {
        // Safe casting from Redis object mapper
        Object data = redisTemplate.opsForValue().get(REG_PREFIX + email);
        if (data instanceof java.util.LinkedHashMap) {
            // Jackson might read it back as a map if not carefully typed
            var map = (java.util.LinkedHashMap<?, ?>) data;
            RegisterUserRequest req = new RegisterUserRequest();
            req.setUsername((String) map.get("username"));
            req.setEmail((String) map.get("email"));
            req.setPassword((String) map.get("password"));
            return req;
        }
        return (RegisterUserRequest) data;
    }

    public void clearOtpAndRegistration(String email) {
        redisTemplate.delete(OTP_PREFIX + email);
        redisTemplate.delete(REG_PREFIX + email);
    }

    public String generateAndSaveResetOtp(String email) {
        // Generate 6 digit OTP
        String otp = String.format("%06d", new Random().nextInt(999999));

        // Store Reset OTP in Redis (Valid for 10 minutes)
        redisTemplate.opsForValue().set(RESET_PREFIX + email, otp, 10, TimeUnit.MINUTES);

        return otp;
    }

    public String getResetOtp(String email) {
        return (String) redisTemplate.opsForValue().get(RESET_PREFIX + email);
    }

    public void clearResetOtp(String email) {
        redisTemplate.delete(RESET_PREFIX + email);
    }
}