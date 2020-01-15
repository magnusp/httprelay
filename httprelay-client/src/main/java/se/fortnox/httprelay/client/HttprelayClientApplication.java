package se.fortnox.httprelay.client;

import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.DefaultPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.messaging.rsocket.MetadataExtractor;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import se.fortnox.httprelay.server.DataFrame;

import javax.annotation.PreDestroy;
import java.util.concurrent.CountDownLatch;

@SpringBootApplication
public class HttprelayClientApplication {
    private final Logger log = LoggerFactory.getLogger(HttprelayClientApplication.class);
    private RSocket socket;

    public static void main(String[] args) {
        SpringApplication.run(HttprelayClientApplication.class, args);
    }

    @Bean
    public Mono<RSocket> rSocket() {
        return RSocketFactory
                .connect()
                .dataMimeType(MimeTypeUtils.APPLICATION_JSON_VALUE)
                .metadataMimeType("message/x.rsocket.routing.v0")
                .frameDecoder(PayloadDecoder.ZERO_COPY)
                .transport(TcpClientTransport.create(7000))
                .start()
                .doOnNext(socket -> {
                    this.socket = socket;
                    log.info("Connected to RSocket");
                })
                .cache();
    }

    @PreDestroy
    public void destroy() {
        socket.dispose();
        log.info("Shutdown");
    }

    @Bean
    public Mono<RSocketRequester> rSocketRequester(Mono<RSocket> rSocket, RSocketStrategies strategies) {
        return rSocket
                .map(socket -> RSocketRequester.wrap(socket, MimeTypeUtils.APPLICATION_JSON, MimeTypeUtils.parseMimeType("message/x.rsocket.routing.v0"), strategies))
                .cache();
    }

    @Bean
    public ApplicationRunner consumer(Mono<RSocketRequester> requester) {
        return args -> {
            requester
                    .flatMapMany(this::getData)
                    .map(DataFrame::getValue)
            .subscribe(log::info);
        };
    }

    private Flux<DataFrame> getData(RSocketRequester requester) {
        return requester
                .route("data")
                .data(DefaultPayload.create(""))
                .retrieveFlux(DataFrame.class);
    }
}
