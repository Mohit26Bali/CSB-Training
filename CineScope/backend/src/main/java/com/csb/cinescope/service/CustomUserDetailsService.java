package com.csb.cinescope.service;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.csb.cinescope.entity.User;
import com.csb.cinescope.repository.UserRepository;
import com.csb.cinescope.security.CineScopeUserDetails;

import java.util.List;

/**
 * Teaches Spring Security how to find a user: loads our {@link User} and converts it to Spring's {@code UserDetails}
 * (username, BCrypt hash, the authority for the account's role ({@code ROLE_USER} or {@code ROLE_ADMIN}) and its token version, enabled flag).
 *
 * <p>Used twice: by the {@code AuthenticationManager} while {@link AuthService#login} checks a password, and by
 * {@code security/JwtAuthenticationFilter} on every request that carries a token. Data comes from {@code repository/UserRepository}.
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
        return new CineScopeUserDetails(user.getUsername(), user.getPassword(), user.isEnabled(),
                List.of(new SimpleGrantedAuthority(user.getRole().authority())), user.getTokenVersion());
    }
}
