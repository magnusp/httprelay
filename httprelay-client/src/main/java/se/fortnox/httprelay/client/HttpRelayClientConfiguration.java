package se.fortnox.httprelay.client;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.cbor.Jackson2CborDecoder;
import org.springframework.http.codec.cbor.Jackson2CborEncoder;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class HttpRelayClientConfiguration {
    @Value(value = "${kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    KafkaSender<Integer, byte[]> kafkaSender() {
        final Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, IntegerSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        final SenderOptions<Integer, byte[]> producerOptions = SenderOptions.create(producerProps);

        return KafkaSender.create(producerOptions);
    }

    @Bean
    public RSocketStrategies rSocketStrategies() {
        return RSocketStrategies.builder()
                .encoders(encoders -> {
                    encoders.add(new Jackson2CborEncoder());
                    encoders.add(new SimpleAuthenticationEncoder());
                })
                .decoders(decoders -> decoders.add(new Jackson2CborDecoder()))
                .build();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "application.runner",
            value = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public ApplicationRunner consumer(HttpRelayClient httpRelayClient) {
        return args -> httpRelayClient.establish().subscribe();
    }
}
