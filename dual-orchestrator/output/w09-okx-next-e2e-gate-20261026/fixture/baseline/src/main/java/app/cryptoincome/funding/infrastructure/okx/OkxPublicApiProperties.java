package app.cryptoincome.funding.infrastructure.okx;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("okx.public-api")
public record OkxPublicApiProperties(URI baseUrl, Duration connectTimeout, Duration requestTimeout) {
    public OkxPublicApiProperties {
        baseUrl = baseUrl == null ? URI.create("https://www.okx.com") : baseUrl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(10) : requestTimeout;
        if (!baseUrl.isAbsolute()) {
            throw new IllegalArgumentException("OKX baseUrl 必须是绝对地址");
        }
        if (connectTimeout.isNegative() || connectTimeout.isZero()
                || requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("OKX 超时时间必须大于 0");
        }
    }
}
