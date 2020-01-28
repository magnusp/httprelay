package se.fortnox.httprelay.server;

import org.springframework.stereotype.Component;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;

@Component
public class DataPublisher {
    private EmitterProcessor<byte[]> emitterProcessor = EmitterProcessor.create(false);

    public void provide(byte[] dataFrame) {
        if (emitterProcessor.hasDownstreams()) {
            emitterProcessor.onNext(dataFrame);
        }

    }

    public Flux<byte[]> stream() {
        return emitterProcessor;
    }
}
