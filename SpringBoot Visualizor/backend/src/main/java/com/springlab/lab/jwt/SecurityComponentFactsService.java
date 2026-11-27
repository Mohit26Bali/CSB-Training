package com.springlab.lab.jwt;

import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.List;

/**
 * Reports what the running application's security configuration ACTUALLY consists of, read from the Spring context.
 * Only class names and counts are exposed: no keys, secrets, property values or user data.
 */
@Service
public class SecurityComponentFactsService {

    public record FilterChainFact(String matcher, List<String> filters) {
    }

    public record AuthenticationManagerFact(String type, List<String> providers) {
    }

    public record BeanFact(String type, int beanCount) {
    }

    public record LabFact(String decoderType, boolean decoderIsSpringBean, boolean usesSeparateKey) {
    }

    public record Facts(List<FilterChainFact> filterChains, AuthenticationManagerFact authenticationManager,
                        String userDetailsService, String passwordEncoder, BeanFact jwtDecoder, BeanFact jwtEncoder,
                        int secretKeyBeans, String jwtAuthenticationConverter, LabFact lab) {
    }

    private final ApplicationContext context;
    private final FilterChainProxy filterChainProxy;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final LabJwtService lab;

    public SecurityComponentFactsService(ApplicationContext context, FilterChainProxy filterChainProxy,
                                         AuthenticationManager authenticationManager,
                                         UserDetailsService userDetailsService, PasswordEncoder passwordEncoder,
                                         LabJwtService lab) {
        this.context = context;
        this.filterChainProxy = filterChainProxy;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.lab = lab;
    }

    public Facts facts() {
        JwtDecoder realDecoder = context.getBean(JwtDecoder.class);
        JwtEncoder realEncoder = context.getBean(JwtEncoder.class);
        return new Facts(
                filterChainProxy.getFilterChains().stream().map(SecurityComponentFactsService::chainFact).toList(),
                managerFact(),
                userDetailsService.getClass().getSimpleName(),
                passwordEncoder.getClass().getSimpleName(),
                new BeanFact(realDecoder.getClass().getSimpleName(), context.getBeanNamesForType(JwtDecoder.class).length),
                new BeanFact(realEncoder.getClass().getSimpleName(), context.getBeanNamesForType(JwtEncoder.class).length),
                context.getBeanNamesForType(SecretKey.class).length,
                context.getBean(com.springlab.security.DatabaseBackedJwtAuthenticationConverter.class).getClass()
                        .getSimpleName(),
                new LabFact(lab.decoder().getClass().getSimpleName(), context.getBeansOfType(JwtDecoder.class)
                        .containsValue(lab.decoder()), lab.decoder() != realDecoder));
    }

    private static FilterChainFact chainFact(SecurityFilterChain chain) {
        String matcher = chain instanceof DefaultSecurityFilterChain d ? d.getRequestMatcher().toString() : "(custom)";
        return new FilterChainFact(matcher, chain.getFilters().stream().map(f -> f.getClass().getSimpleName()).toList());
    }

    /** The login manager is declared explicitly in SecurityConfig as a ProviderManager, so its providers are public API. */
    private AuthenticationManagerFact managerFact() {
        List<String> providers = authenticationManager instanceof ProviderManager pm
                ? pm.getProviders().stream().map(AuthenticationProvider::getClass).map(Class::getSimpleName).toList()
                : List.of(); // any other manager type: report no providers rather than guess
        return new AuthenticationManagerFact(authenticationManager.getClass().getSimpleName(), providers);
    }
}
