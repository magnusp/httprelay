package se.fortnox.httprelay.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.security.InvalidKeyException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {"SLACK_SHARED_SECRET = stubSecret"})
class WebhookHandlerTest {

    WebTestClient webTestClient;

    @Autowired
    RouterFunction<ServerResponse> routerFunction;

    @MockBean
    SignatureValidator signatureValidator;

    @BeforeEach
    public void setUp() {
        webTestClient = WebTestClient.bindToRouterFunction(routerFunction).build();

    }

    @Test
    void shouldAcceptUrlVerification() throws InvalidKeyException {
        String challengeUnderTest = "3eZbrw1aBm2rZgRNFdxV2595E9CY3gmdALWMmHkvFXO7tYXAYM8P";
        when(signatureValidator.validate(any(byte[].class), any())).thenReturn(true);

        webTestClient.post()
                .uri("/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(httpHeaders -> {
                    httpHeaders.add("X-Slack-Request-Timestamp", "0");
                    httpHeaders.add("X-Slack-Signature", "stubSignature");
                })
                .body(BodyInserters.fromValue("{\n" +
                        "  \"token\": \"Jhj5dZrVaK7ZwHHjRyZWjbDl\",\n" +
                        "  \"challenge\": \"" + challengeUnderTest + "\",\n" +
                        "  \"type\": \"url_verification\"\n" +
                        "}"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(challengeUnderTest);
    }

    @Test
    void shouldDenyUrlVerification() throws InvalidKeyException {
        String challengeUnderTest = "3eZbrw1aBm2rZgRNFdxV2595E9CY3gmdALWMmHkvFXO7tYXAYM8P";
        when(signatureValidator.validate(any(byte[].class), any())).thenReturn(false);

        webTestClient.post()
                .uri("/webhook")
                .accept(MediaType.APPLICATION_JSON)
                .headers(httpHeaders -> {
                    httpHeaders.add("X-Slack-Request-Timestamp", "0");
                    httpHeaders.add("X-Slack-Signature", "stubSignature");
                })
                .body(BodyInserters.fromValue("{\n" +
                        "  \"token\": \"Jhj5dZrVaK7ZwHHjRyZWjbDl\",\n" +
                        "  \"challenge\": \"" + challengeUnderTest + "\",\n" +
                        "  \"type\": \"url_verification\"\n" +
                        "}"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class).isEqualTo("Invalid signature");
    }
}