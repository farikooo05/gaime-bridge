package com.dynorix.gaimebridge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/ui/**",
                                "/favicon.ico",
                                "/.well-known/**",
                                "/actuator/health",
                                "/actuator/info",
                                "/swagger-ui/**",
                                "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers("/api/v1/documents/**", "/api/v1/sync/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    UserDetailsService userDetailsService(ApplicationSecurityProperties properties, PasswordEncoder passwordEncoder) {
        if (properties.apiUsername() == null || properties.apiUsername().isBlank()) {
            throw new IllegalStateException("API username is not configured");
        }
        if (properties.apiPassword() == null || properties.apiPassword().isBlank()) {
            throw new IllegalStateException("API password is not configured");
        }
        return new InMemoryUserDetailsManager(User.withUsername(properties.apiUsername())
                .password(passwordEncoder.encode(properties.apiPassword()))
                .roles("API")
                .build());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
