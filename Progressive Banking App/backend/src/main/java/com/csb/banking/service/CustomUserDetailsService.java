package com.csb.banking.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.csb.banking.entity.User;
import com.csb.banking.repository.UserRepository;

/**
 * Teaches Spring Security how to find a user: loads our {@link User} entity and converts it to Spring's
 * {@code UserDetails} (username, BCrypt hash, authority {@code ROLE_<role>}, enabled flag).
 *
 * <p>Used in two places: the {@code AuthenticationManager} calls it while checking the password during login
 * ({@link AuthService#login}), and {@code security/JwtAuthenticationFilter} calls it on every token-authenticated
 * request. Data comes from {@code repository/UserRepository}.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole().name())
                .disabled(!user.isEnabled())
                .build();
    }
}
