package app.cryptoincome.funding.infrastructure.okx;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(OkxPublicApiProperties.class)
public class OkxPublicApiConfiguration {
    @Bean
    @Qualifier("okxHttpClient")
    HttpClient okxHttpClient(OkxPublicApiProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
