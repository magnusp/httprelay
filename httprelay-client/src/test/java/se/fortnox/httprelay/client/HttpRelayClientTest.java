package se.fortnox.httprelay.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;
import reactor.test.StepVerifier;

import java.nio.charset.Charset;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class HttpRelayClientTest {
    @Autowired
    HttpRelayClient httpRelayClient;

    @MockBean
    KafkaSender<Integer, byte[]> kafkaSender;

    @Mock
    SenderResult<Integer> senderResult;

    @BeforeEach
    public void setup() {
        doReturn(Flux.just(senderResult)).when(kafkaSender).send(argThat(argument -> {
            SenderRecord<Integer, byte[], Object> value = Mono.from(argument).block();
            String s = new String(Objects.requireNonNull(value).value(), Charset.defaultCharset());
            return s.equals("Message");
        }));
    }

    @Test
    void shouldPublishMessageToKafka() {
        httpRelayClient.establish()
                .as(StepVerifier::create)
                .verifyComplete();
        verify(kafkaSender, times(2)).send(anyPublisher());
    }

    private Publisher<SenderRecord<Integer, byte[], Integer>> anyPublisher() {
        return any();
    }

    @TestConfiguration
    @EnableRSocketSecurity
    public static class MyTestConfiguration {
        @Bean
        public MapReactiveUserDetailsService userDetailsService() {
            UserDetails user = User.withDefaultPasswordEncoder() // TODO Dangerous
                    .username("user")
                    .password("user")
                    .roles("USER")
                    .build();
            return new MapReactiveUserDetailsService(user);
        }

        @RestController
        public static class TestableController {
            @MessageMapping("/events")
            public Flux<String> produceEvents() {
                return Flux.just("Message").repeat(1);
            }
        }
    }
}