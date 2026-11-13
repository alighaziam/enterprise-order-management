package io.github.alighaziam.eom.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/favicon.ico", "/assets/**",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasAuthority("SCOPE_operations.admin")
                        .requestMatchers(HttpMethod.GET, "/api/v1/customers/**").hasAuthority("SCOPE_customers.read")
                        .requestMatchers("/api/v1/customers/**").hasAuthority("SCOPE_customers.write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").hasAuthority("SCOPE_catalog.read")
                        .requestMatchers("/api/v1/products/**").hasAuthority("SCOPE_catalog.write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/inventory/**").hasAuthority("SCOPE_inventory.read")
                        .requestMatchers("/api/v1/inventory/**").hasAuthority("SCOPE_inventory.write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/**").hasAuthority("SCOPE_orders.read")
                        .requestMatchers("/api/v1/orders/**").hasAuthority("SCOPE_orders.write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/payments/**").hasAuthority("SCOPE_payments.read")
                        .requestMatchers("/api/v1/payments/**").hasAuthority("SCOPE_payments.write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/shipments/**").hasAuthority("SCOPE_shipping.read")
                        .requestMatchers("/api/v1/shipments/**").hasAuthority("SCOPE_shipping.write")
                        .requestMatchers(HttpMethod.POST, "/api/v1/checkouts/*/reconcile")
                                .hasAuthority("SCOPE_operations.admin")
                        .requestMatchers(HttpMethod.GET, "/api/v1/checkouts/**").hasAuthority("SCOPE_checkout.read")
                        .requestMatchers("/api/v1/checkouts/**").hasAuthority("SCOPE_checkout.write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/outbox/events/**").hasAuthority("SCOPE_outbox.read")
                        .requestMatchers("/api/v1/outbox/**").hasAuthority("SCOPE_operations.admin")
                        .requestMatchers("/api/v1/**").denyAll()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * Explicit opt-out is reserved for isolated integration tests. Production defaults to secured.
     */
    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "false")
    SecurityFilterChain isolatedTestSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
