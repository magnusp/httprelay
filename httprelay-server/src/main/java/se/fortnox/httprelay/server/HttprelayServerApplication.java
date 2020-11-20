package se.fortnox.httprelay.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
public class HttprelayServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(HttprelayServerApplication.class, args);
	}

	@Bean
	RouterFunction<ServerResponse> configureRouter(WebhookHandler webhookHandler) {
		return route(POST("/webhook"), webhookHandler::handle);
	}

}
