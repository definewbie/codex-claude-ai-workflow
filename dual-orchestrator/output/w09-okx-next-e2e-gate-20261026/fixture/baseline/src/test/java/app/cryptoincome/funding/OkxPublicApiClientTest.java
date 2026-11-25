package app.cryptoincome.funding;

import app.cryptoincome.funding.domain.FundingFormulaType;
import app.cryptoincome.funding.domain.FundingRateObservation;
import app.cryptoincome.funding.domain.FundingRateSourceException;
import app.cryptoincome.funding.domain.FundingSettlementMethod;
import app.cryptoincome.funding.infrastructure.okx.OkxPublicApiClient;
import app.cryptoincome.funding.infrastructure.okx.OkxPublicApiProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OkxPublicApiClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void shouldMapExpectedAndRealizedRatesWithoutLosingProviderSemantics() throws IOException {
        String response = """
                {"code":"0","msg":"","data":[{
                  "instType":"SWAP","instId":"BTC-USDT-SWAP","formulaType":"withRate",
                  "fundingRate":"0.000123","realizedRate":"0.000120",
                  "fundingTime":"1767225600000","method":"current_period"
                }]}
                """;
        OkxPublicApiClient client = clientResponding(200, response);

        List<FundingRateObservation> result = client.fetchLatest("BTC-USDT-SWAP", 10);

        assertThat(result).hasSize(1);
        FundingRateObservation fact = result.getFirst();
        assertThat(fact.predictedRate()).isEqualByComparingTo("0.000123");
        assertThat(fact.realizedRate()).isEqualByComparingTo("0.000120");
        assertThat(fact.formulaType()).isEqualTo(FundingFormulaType.WITH_RATE);
        assertThat(fact.settlementMethod()).isEqualTo(FundingSettlementMethod.CURRENT_PERIOD);
        assertThat(fact.fundingTime()).isEqualTo(Instant.ofEpochMilli(1767225600000L));
        assertThat(fact.payloadHash()).hasSize(64);
    }

    @Test
    void shouldClassifyOkxRateLimitAsTypedRetryableFailure() throws IOException {
        OkxPublicApiClient client = clientResponding(200,
                "{\"code\":\"50011\",\"msg\":\"Too Many Requests\",\"data\":[]}");

        assertThatThrownBy(() -> client.fetchLatest("BTC-USDT-SWAP", 10))
                .isInstanceOfSatisfying(FundingRateSourceException.class, failure -> {
                    assertThat(failure.category()).isEqualTo(FundingRateSourceException.Category.API_REJECTED);
                    assertThat(failure.providerCode()).isEqualTo("50011");
                    assertThat(failure.retryable()).isTrue();
                });
    }

    @Test
    void shouldRejectEvidenceForAnotherInstrument() throws IOException {
        String response = """
                {"code":"0","msg":"","data":[{
                  "instType":"SWAP","instId":"ETH-USDT-SWAP","formulaType":"withRate",
                  "fundingRate":"0.0001","realizedRate":"0.0001",
                  "fundingTime":"1767225600000","method":"current_period"
                }]}
                """;
        OkxPublicApiClient client = clientResponding(200, response);

        assertThatThrownBy(() -> client.fetchLatest("BTC-USDT-SWAP", 10))
                .isInstanceOfSatisfying(FundingRateSourceException.class, failure ->
                        assertThat(failure.category()).isEqualTo(FundingRateSourceException.Category.INVALID_RESPONSE));
    }

    @Test
    void shouldParseRecordedRealOkxResponse() throws IOException {
        String response = resource("/okx/funding-rate-history-btc-usdt-swap-2026-08-15.json");
        List<FundingRateObservation> result = clientResponding(200, response)
                .fetchLatest("BTC-USDT-SWAP", 5);

        assertThat(result).hasSize(5);
        assertThat(result).allSatisfy(fact -> {
            assertThat(fact.instrumentId()).isEqualTo("BTC-USDT-SWAP");
            assertThat(fact.formulaType()).isEqualTo(FundingFormulaType.WITH_RATE);
            assertThat(fact.settlementMethod()).isEqualTo(FundingSettlementMethod.CURRENT_PERIOD);
            assertThat(fact.realizedRate()).isNotNull();
        });
    }

    @Test
    void shouldKeepSemanticHashStableAcrossEquivalentDecimalFormatsAndJsonOrder() throws IOException {
        String response = """
                {"code":"0","msg":"","data":[
                  {"instType":"SWAP","instId":"BTC-USDT-SWAP","formulaType":"withRate",
                   "fundingRate":"0.000120","realizedRate":"-0","fundingTime":"1767225600000",
                   "method":"current_period"},
                  {"method":"current_period","fundingTime":"1767225600000","realizedRate":"0",
                   "fundingRate":"1.20E-4","formulaType":"withRate","instId":"BTC-USDT-SWAP",
                   "instType":"SWAP"}
                ]}
                """;
        List<FundingRateObservation> result = clientResponding(200, response)
                .fetchLatest("BTC-USDT-SWAP", 2);
        assertThat(result.get(0).payloadHash()).isEqualTo(result.get(1).payloadHash());
    }

    @Test
    void shouldRejectLimitOutsideProviderContractBeforeNetworkCall() throws IOException {
        OkxPublicApiClient client = clientResponding(200, "{\"code\":\"0\",\"data\":[],\"msg\":\"\"}");
        assertThatThrownBy(() -> client.fetchLatest("BTC-USDT-SWAP", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.fetchLatest("BTC-USDT-SWAP", 401))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(client.fetchLatest("BTC-USDT-SWAP", 1)).isEmpty();
    }

    @Test
    void shouldRejectUnknownProviderEnumAsInvalidResponse() throws IOException {
        String response = """
                {"code":"0","msg":"","data":[{
                  "instType":"SWAP","instId":"BTC-USDT-SWAP","formulaType":"futureFormula",
                  "fundingRate":"0.0001","realizedRate":"0.0001",
                  "fundingTime":"1767225600000","method":"current_period"
                }]}
                """;
        OkxPublicApiClient client = clientResponding(200, response);
        assertThatThrownBy(() -> client.fetchLatest("BTC-USDT-SWAP", 1))
                .isInstanceOfSatisfying(FundingRateSourceException.class, failure ->
                        assertThat(failure.category()).isEqualTo(FundingRateSourceException.Category.INVALID_RESPONSE));
    }

    @Test
    void shouldClassifyHttpRateLimitAsRetryableHttpError() throws IOException {
        OkxPublicApiClient client = clientResponding(429, "{\"code\":\"0\",\"msg\":\"\"}");

        assertThatThrownBy(() -> client.fetchLatest("BTC-USDT-SWAP", 10))
                .isInstanceOfSatisfying(FundingRateSourceException.class, failure -> {
                    assertThat(failure.category()).isEqualTo(FundingRateSourceException.Category.HTTP_ERROR);
                    assertThat(failure.providerCode()).isEqualTo("429");
                    assertThat(failure.retryable()).isTrue();
                });
    }

    @Test
    void shouldClassifyHttpServerFailureAsRetryableHttpError() throws IOException {
        OkxPublicApiClient client = clientResponding(503, "service unavailable");

        assertThatThrownBy(() -> client.fetchLatest("BTC-USDT-SWAP", 10))
                .isInstanceOfSatisfying(FundingRateSourceException.class, failure -> {
                    assertThat(failure.category()).isEqualTo(FundingRateSourceException.Category.HTTP_ERROR);
                    assertThat(failure.providerCode()).isEqualTo("503");
                    assertThat(failure.retryable()).isTrue();
                });
    }

    @Test
    void shouldClassifyHttpNotFoundAsNonRetryableHttpError() throws IOException {
        OkxPublicApiClient client = clientResponding(404, "not found");

        assertThatThrownBy(() -> client.fetchLatest("BTC-USDT-SWAP", 10))
                .isInstanceOfSatisfying(FundingRateSourceException.class, failure -> {
                    assertThat(failure.category()).isEqualTo(FundingRateSourceException.Category.HTTP_ERROR);
                    assertThat(failure.providerCode()).isEqualTo("404");
                    assertThat(failure.retryable()).isFalse();
                });
    }

    @Test
    void shouldClassifyConnectionFailureAsNetworkError() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            AtomicBoolean running = new AtomicBoolean(true);
            Thread connectionKiller = new Thread(() -> {
                while (running.get()) {
                    try (Socket socket = serverSocket.accept()) {
                        socket.setSoLinger(true, 0);
                    } catch (IOException stopAccepting) {
                        return;
                    }
                }
            });
            connectionKiller.setDaemon(true);
            connectionKiller.start();
            OkxPublicApiClient client = clientForBaseUrl(
                    URI.create("http://127.0.0.1:" + serverSocket.getLocalPort()),
                    Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneOffset.UTC));

            assertThatThrownBy(() -> client.fetchLatest("BTC-USDT-SWAP", 10))
                    .isInstanceOfSatisfying(FundingRateSourceException.class, failure ->
                            assertThat(failure.category()).isEqualTo(FundingRateSourceException.Category.NETWORK));
            running.set(false);
        }
    }

    private static OkxPublicApiClient clientForBaseUrl(URI baseUrl, Clock clock) {
        return new OkxPublicApiClient(
                HttpClient.newHttpClient(), new ObjectMapper(),
                new OkxPublicApiProperties(baseUrl, Duration.ofSeconds(1), Duration.ofSeconds(2)), clock);
    }

    @Test
    void shouldRequestOlderPageWithAfterCursorParameter() throws IOException {
        AtomicReference<String> query = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v5/public/funding-rate-history", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            byte[] bytes = "{\"code\":\"0\",\"msg\":\"data\":[]}".getBytes(StandardCharsets.UTF_8);
            bytes = "{\"code\":\"0\",\"msg\":\"\",\"data\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        OkxPublicApiClient client = clientForBaseUrl(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneOffset.UTC));

        List<FundingRateObservation> result = client.fetchPage(
                "BTC-USDT-SWAP", 100, Instant.ofEpochMilli(1767225600000L));

        assertThat(result).isEmpty();
        assertThat(query.get()).contains("instId=BTC-USDT-SWAP");
        assertThat(query.get()).contains("after=1767225600000");
        assertThat(query.get()).contains("limit=100");
    }

    private static String resource(String path) throws IOException {
        try (InputStream input = OkxPublicApiClientTest.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("fixture 不存在: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private OkxPublicApiClient clientResponding(int status, String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v5/public/funding-rate-history", exchange -> {
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        URI baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        Clock clock = Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneOffset.UTC);
        return new OkxPublicApiClient(
                HttpClient.newHttpClient(), new ObjectMapper(),
                new OkxPublicApiProperties(baseUrl, Duration.ofSeconds(1), Duration.ofSeconds(2)), clock);
    }
}
