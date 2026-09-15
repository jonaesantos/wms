package io.tenoro.app.infra.seed;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wms.seed.enabled", havingValue = "true", matchIfMissing = true)
public class WarehouseSeedRunner implements CommandLineRunner {

    private final WarehouseSeed seed;

    public WarehouseSeedRunner(WarehouseSeed seed) {
        this.seed = seed;
    }

    @Override
    public void run(String... args) {
        seed.load();
    }
}
