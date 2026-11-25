package app.cryptoincome.grid.sync;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class CandleSyncConfiguration {
    @Bean(name = "candleSyncExecutor", destroyMethod = "close")
    ExecutorService candleSyncExecutor() {
        return Executors.newSingleThreadExecutor(Thread.ofVirtual().name("candle-sync-", 0).factory());
    }
}
