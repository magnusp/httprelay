package se.fortnox.httprelay.server;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

@ExtendWith( SpringExtension.class )
@WebFluxTest(SlackController.class)
@TestPropertySource(properties = {"SLACK_SHARED_SECRET = foo"})
public class SlackControllerTest {

    @Autowired
    WebTestClient webTestClient;

    @Test
    @Disabled
    public void testAcceptUrlVerification() {
        webTestClient.post()
                .uri("/webhook")
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue("{\n" +
                        "  \"token\": \"Jhj5dZrVaK7ZwHHjRyZWjbDl\",\n" +
                        "  \"challenge\": \"3eZbrw1aBm2rZgRNFdxV2595E9CY3gmdALWMmHkvFXO7tYXAYM8P\",\n" +
                        "  \"type\": \"url_verification\"\n" +
                        "}"))
                .exchange()
                .expectStatus().isOk();
    }
}