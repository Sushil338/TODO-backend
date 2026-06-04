package com.manager.TODO.Services;

import com.manager.TODO.Models.User;
import com.manager.TODO.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        // Try searching by username first; if not present, check by email address
        User user = userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found with identifier: " + identifier));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername()) // This sets principal.getName() to username across the app
                .password(user.getPassword())
                .authorities("ROLE_USER")
                .build();
    }
}