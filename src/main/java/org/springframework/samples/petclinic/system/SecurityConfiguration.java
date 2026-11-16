/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.system;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfiguration {

	static final String ROLE_RECEPTIONIST = "RECEPTIONIST";

	static final String ROLE_VET = "VET";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(authorize -> authorize
				// Public pages
				.requestMatchers("/", "/oups", "/login").permitAll()
				// Static resources
				.requestMatchers("/webjars/**", "/resources/**", "/static/**", "/css/**", "/js/**", "/images/**",
						"/resources/css/**", "/resources/images/**", "/resources/js/**", "/resources/images/**",
						"/resources/js/**").permitAll()
				// Vets list is read-only (HTML + JSON)
				.requestMatchers(HttpMethod.GET, "/vets", "/vets.html").permitAll()
				// Owner read-only pages require authentication (both roles)
				.requestMatchers(HttpMethod.GET, "/owners/find", "/owners", "/owners/*")
					.hasAnyRole(ROLE_RECEPTIONIST, ROLE_VET)
				// Owner write operations (receptionist only)
				.requestMatchers("/owners/new", "/owners/*/edit").hasRole(ROLE_RECEPTIONIST)
				// Pet write operations (receptionist only)
				.requestMatchers("/owners/*/pets/new", "/owners/*/pets/*/edit").hasRole(ROLE_RECEPTIONIST)
				// Visit creation (vet allowed; receptionist allowed too if needed)
				.requestMatchers("/owners/*/pets/*/visits/new").hasAnyRole(ROLE_VET, ROLE_RECEPTIONIST)
				// Any other endpoint requires authentication
				.anyRequest().authenticated())
			.formLogin(form -> form
				.loginPage("/login")
				.permitAll())
			.logout(logout -> logout.logoutSuccessUrl("/"))
			.exceptionHandling(exceptions -> exceptions.accessDeniedPage("/error/403"));
		return http.build();
	}

	@Bean
	UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
		UserDetails receptionist = User.withUsername("receptionist")
			.password(passwordEncoder.encode("receptionist"))
			.roles(ROLE_RECEPTIONIST)
			.build();

		UserDetails vet = User.withUsername("vet").password(passwordEncoder.encode("vet")).roles(ROLE_VET).build();

		return new InMemoryUserDetailsManager(receptionist, vet);
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

}
