package se.fortnox.httprelay.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;


@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
	@Bean
	public MapReactiveUserDetailsService userDetailsService() {
		UserDetails user = User.withDefaultPasswordEncoder() // TODO Dangerous
			.username("user")
			.password("user")
			.roles("USER")
			.build();
		return new MapReactiveUserDetailsService(user);
	}

	@Bean
	public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity serverHttpSecurity) {
		return serverHttpSecurity
				.requestCache().requestCache(NoOpServerRequestCache.getInstance())
				.and()
				.securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
				.httpBasic().disable()
				.formLogin().disable()
				.csrf().disable()
				.logout().disable()
				.authorizeExchange().anyExchange().permitAll().and().build();
	}
}