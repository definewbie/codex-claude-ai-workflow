package app.cryptoincome.grid;

import app.cryptoincome.funding.infrastructure.okx.OkxPublicApiProperties;
import app.cryptoincome.grid.domain.GridDuration;
import app.cryptoincome.grid.domain.GridType;
import app.cryptoincome.grid.infrastructure.okx.OkxGridParameterClient;
import app.cryptoincome.grid.domain.GridSourceException;
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

class OkxGridParameterClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void mapsPublicAiParameterAsTypedSnapshotAndSendsDuration() throws IOException {
        AtomicReference<String> query = new AtomicReference<>();
        OkxGridParameterClient client = clientResponding(200, """
                {"code":"0","msg":"","data":[{
                  "algoOrdType":"grid","annualizedRate":"0.2487","ccy":"USDT",
                  "direction":"","duration":"180D","gridNum":"50","instId":"BTC-USDT",
                  "lever":"0","maxPx":"76382.5","minInvestment":"100",
                  "minPx":"47483.2","perGridProfitRatio":"0.0085",
                  "perMaxProfitRate":"0.0106","perMinProfitRate":"0.0060",
                  "runType":"1","sourceCcy":""
                }]}
                """, query);

        var snapshot = client.fetch("BTC-USDT", GridDuration.D180);

        assertThat(query.get()).contains("instId=BTC-USDT", "algoOrdType=grid", "duration=180D");
        assertThat(snapshot.instrumentId()).isEqualTo("BTC-USDT");
        assertThat(snapshot.duration()).isEqualTo(GridDuration.D180);
        assertThat(snapshot.gridType()).isEqualTo(GridType.ARITHMETIC);
        assertThat(snapshot.lowerPrice()).isEqualByComparingTo("47483.2");
        assertThat(snapshot.upperPrice()).isEqualByComparingTo("76382.5");
        assertThat(snapshot.gridCount()).isEqualTo(50);
        assertThat(snapshot.annualizedRate()).isEqualByComparingTo("0.2487");
        assertThat(snapshot.observedAt()).isEqualTo(Instant.parse("2026-08-19T08:00:00Z"));
        assertThat(snapshot.payloadHash()).hasSize(64);
    }

    @Test
    void rejectsUnknownGridTypeAndMismatchedInstrumentAsInvalidResponse() throws IOException {
        OkxGridParameterClient unknownType = clientResponding(200, response("BTC-USDT", "9"), new AtomicReference<>());
        assertThatThrownBy(() -> unknownType.fetch("BTC-USDT", GridDuration.D7))
                .isInstanceOfSatisfying(GridSourceException.class, failure ->
                        assertThat(failure.category()).isEqualTo(GridSourceException.Category.INVALID_RESPONSE));

        OkxGridParameterClient wrongInstrument = clientResponding(200, response("ETH-USDT", "1"), new AtomicReference<>());
        assertThatThrownBy(() -> wrongInstrument.fetch("BTC-USDT", GridDuration.D7))
                .isInstanceOfSatisfying(GridSourceException.class, failure ->
                        assertThat(failure.category()).isEqualTo(GridSourceException.Category.INVALID_RESPONSE));
    }

    @Test
    void classifiesProviderRateLimitAsTypedRetryableFailure() throws IOException {
        OkxGridParameterClient client = clientResponding(200,
                "{\"code\":\"50011\",\"msg\":\"Too Many Requests\",\"data\":[]}", new AtomicReference<>());

        assertThatThrownBy(() -> client.fetch("BTC-USDT", GridDuration.D30))
                .isInstanceOfSatisfying(GridSourceException.class, failure -> {
                    assertThat(failure.category()).isEqualTo(GridSourceException.Category.API_REJECTED);
                    assertThat(failure.providerCode()).isEqualTo("50011");
                    assertThat(failure.retryable()).isTrue();
                });
    }

    private OkxGridParameterClient clientResponding(
            int status, String body, AtomicReference<String> query) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v5/tradingBot/grid/ai-param", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        URI baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        return new OkxGridParameterClient(
                HttpClient.newHttpClient(), new ObjectMapper(),
                new OkxPublicApiProperties(baseUrl, Duration.ofSeconds(1), Duration.ofSeconds(2)),
                Clock.fixed(Instant.parse("2026-08-19T08:00:00Z"), ZoneOffset.UTC));
    }

    private static String response(String instrument, String runType) {
        return """
                {"code":"0","msg":"","data":[{
                  "algoOrdType":"grid","annualizedRate":"0.1","ccy":"USDT",
                  "direction":"","duration":"7D","gridNum":"5","instId":"%s",
                  "lever":"0","maxPx":"70000","minInvestment":"100",
                  "minPx":"50000","perGridProfitRatio":"0.01",
                  "perMaxProfitRate":"0.02","perMinProfitRate":"0.01",
                  "runType":"%s","sourceCcy":""
                }]}
                """.formatted(instrument, runType);
    }
}
