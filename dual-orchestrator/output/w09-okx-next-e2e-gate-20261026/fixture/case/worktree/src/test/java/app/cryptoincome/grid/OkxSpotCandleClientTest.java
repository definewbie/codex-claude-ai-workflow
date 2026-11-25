package app.cryptoincome.grid;

import app.cryptoincome.funding.infrastructure.okx.OkxPublicApiProperties;
import app.cryptoincome.grid.domain.CandleBar;
import app.cryptoincome.grid.domain.GridSourceException;
import app.cryptoincome.grid.infrastructure.okx.OkxSpotCandleClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OkxSpotCandleClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void mapsCompletedAndUncompletedCandlesAndPassesOlderThanCursor() throws IOException {
        AtomicReference<String> query = new AtomicReference<>();
        OkxSpotCandleClient client = clientResponding("""
                {"code":"0","msg":"","data":[
                  ["1787011260000","64100","64200","64000","64150","1.2","77000","77000","0"],
                  ["1787011200000","64000","64200","63900","64100","2.3","147000","147000","1"]
                ]}
                """, query);

        var candles = client.fetchPage("BTC-USDT", CandleBar.M1, 300,
                Instant.parse("2026-08-18T00:02:00Z"));

        assertThat(query.get()).contains("instId=BTC-USDT", "bar=1m", "limit=300", "after=1787011320000");
        assertThat(candles).hasSize(2);
        assertThat(candles.get(0).confirmed()).isFalse();
        assertThat(candles.get(1).confirmed()).isTrue();
        assertThat(candles.get(1).open()).isEqualByComparingTo("64000");
        assertThat(candles.get(1).payloadHash()).hasSize(64);
    }

    @Test
    void rejectsImpossibleOhlcAsInvalidResponse() throws IOException {
        OkxSpotCandleClient client = clientResponding("""
                {"code":"0","msg":"","data":[
                  ["1787011200000","64000","63000","63900","64100","2.3","147000","147000","1"]
                ]}
                """, new AtomicReference<>());

        assertThatThrownBy(() -> client.fetchPage("BTC-USDT", CandleBar.M1, 100,
                Instant.parse("2026-08-18T00:02:00Z")))
                .isInstanceOfSatisfying(GridSourceException.class, failure ->
                        assertThat(failure.category()).isEqualTo(GridSourceException.Category.INVALID_RESPONSE));
    }

    private OkxSpotCandleClient clientResponding(String body, AtomicReference<String> query) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v5/market/history-candles", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        URI baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        return new OkxSpotCandleClient(
                HttpClient.newHttpClient(), new ObjectMapper(),
                new OkxPublicApiProperties(baseUrl, Duration.ofSeconds(1), Duration.ofSeconds(2)),
                Clock.fixed(Instant.parse("2026-08-19T08:00:00Z"), ZoneOffset.UTC));
    }
}
