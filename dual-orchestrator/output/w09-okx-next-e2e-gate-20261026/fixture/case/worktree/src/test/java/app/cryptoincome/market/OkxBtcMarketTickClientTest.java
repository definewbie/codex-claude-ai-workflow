package app.cryptoincome.market;

import app.cryptoincome.funding.infrastructure.okx.OkxPublicApiProperties;
import app.cryptoincome.market.infrastructure.okx.OkxBtcMarketTickClient;
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

import static org.assertj.core.api.Assertions.assertThat;

class OkxBtcMarketTickClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() { if (server != null) server.stop(0); }

    @Test
    void mapsActualTickerFieldsAndKeepsProviderTimestamp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v5/market/ticker", exchange -> {
            byte[] bytes = """
                    {"code":"0","msg":"","data":[{"instType":"SPOT","instId":"BTC-USDT",
                    "last":"64185.1","bidPx":"64185.0","askPx":"64185.2","ts":"1787356800123"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        var client = new OkxBtcMarketTickClient(HttpClient.newHttpClient(), new ObjectMapper(),
                new OkxPublicApiProperties(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                        Duration.ofSeconds(1), Duration.ofSeconds(2)),
                Clock.fixed(Instant.parse("2026-08-22T00:00:01Z"), ZoneOffset.UTC));

        var tick = client.fetch("BTC-USDT");

        assertThat(tick.lastPrice()).isEqualByComparingTo("64185.1");
        assertThat(tick.bidPrice()).isEqualByComparingTo("64185.0");
        assertThat(tick.askPrice()).isEqualByComparingTo("64185.2");
        assertThat(tick.providerTimestamp()).isEqualTo(Instant.ofEpochMilli(1787356800123L));
        assertThat(tick.sourceEndpoint()).isEqualTo("/api/v5/market/ticker");
    }
}
