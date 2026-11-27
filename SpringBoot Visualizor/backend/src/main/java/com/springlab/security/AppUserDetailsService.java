package com.springlab.security;

import com.springlab.repository.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Used only by the login flow (DaoAuthenticationProvider); API requests are authenticated from the JWT. */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        com.springlab.entity.User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles(user.getRole().name()) // adds the ROLE_ prefix
                .disabled(!user.isEnabled())
                .build();
    }
}
