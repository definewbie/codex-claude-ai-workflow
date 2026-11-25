package app.cryptoincome.market.infrastructure.okx;

import app.cryptoincome.funding.infrastructure.okx.OkxPublicApiProperties;
import app.cryptoincome.grid.domain.GridSourceException;
import app.cryptoincome.market.domain.BtcMarketTick;
import app.cryptoincome.market.domain.BtcMarketTickSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class OkxBtcMarketTickClient implements BtcMarketTickSource {
    public static final String ENDPOINT = "/api/v5/market/ticker";
    private static final int MAX_ERROR_BODY = 500;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OkxPublicApiProperties properties;
    private final Clock clock;

    public OkxBtcMarketTickClient(
            @Qualifier("okxHttpClient") HttpClient httpClient,
            ObjectMapper objectMapper,
            OkxPublicApiProperties properties,
            Clock clock) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public BtcMarketTick fetch(String instrumentId) {
        if (!"BTC-USDT".equals(instrumentId)) {
            throw new IllegalArgumentException("第一版实时行情只支持 BTC-USDT");
        }
        URI uri = properties.baseUrl().resolve(ENDPOINT + "?instId="
                + URLEncoder.encode(instrumentId, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(properties.requestTimeout()).header("Accept", "application/json").GET().build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException timeout) {
            throw failure(GridSourceException.Category.TIMEOUT, true, "OKX BTC 行情请求超时", timeout);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw failure(GridSourceException.Category.INTERRUPTED, true, "OKX BTC 行情请求被中断", interrupted);
        } catch (IOException network) {
            throw failure(GridSourceException.Category.NETWORK, true, "OKX BTC 行情网络请求失败", network);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;
            throw failure(GridSourceException.Category.HTTP_ERROR, retryable,
                    "OKX BTC 行情 HTTP 错误 " + response.statusCode() + ": " + bounded(response.body()), null);
        }
        return parse(response.body(), instrumentId);
    }

    private BtcMarketTick parse(String body, String instrumentId) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!"0".equals(requiredText(root, "code"))) {
                throw failure(GridSourceException.Category.API_REJECTED, false,
                        "OKX API 拒绝 BTC 行情请求: " + bounded(root.path("msg").asText()), null);
            }
            JsonNode data = root.path("data");
            if (!data.isArray() || data.size() != 1 || !data.get(0).isObject()) {
                throw new IllegalArgumentException("ticker data 必须恰好包含一条对象记录");
            }
            JsonNode row = data.get(0);
            if (!instrumentId.equals(requiredText(row, "instId"))) {
                throw new IllegalArgumentException("ticker 标的不匹配");
            }
            BigDecimal last = requiredDecimal(row, "last");
            BigDecimal bid = optionalDecimal(row, "bidPx");
            BigDecimal ask = optionalDecimal(row, "askPx");
            Instant providerTimestamp = Instant.ofEpochMilli(Long.parseLong(requiredText(row, "ts")));
            String canonical = String.join("|", "OKX", instrumentId, canonical(last),
                    canonical(bid), canonical(ask), Long.toString(providerTimestamp.toEpochMilli()));
            return new BtcMarketTick("OKX", instrumentId, last, bid, ask, providerTimestamp,
                    clock.instant(), ENDPOINT, row.toString(), sha256(canonical));
        } catch (GridSourceException known) {
            throw known;
        } catch (RuntimeException malformed) {
            throw failure(GridSourceException.Category.INVALID_RESPONSE, false,
                    "OKX BTC 行情响应字段无效: " + bounded(malformed.getMessage()), malformed);
        }
    }

    private static BigDecimal requiredDecimal(JsonNode node, String field) {
        return new BigDecimal(requiredText(node, field));
    }

    private static BigDecimal optionalDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() || value.asText().isBlank()
                ? null : new BigDecimal(value.asText());
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("缺少文本字段 " + field);
        }
        return value.asText();
    }

    private static String canonical(BigDecimal value) {
        if (value == null) return "";
        return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JDK 缺少 SHA-256", impossible);
        }
    }

    private static GridSourceException failure(
            GridSourceException.Category category, boolean retryable, String message, Throwable cause) {
        return new GridSourceException(category, null, retryable, message, cause);
    }

    private static String bounded(String value) {
        if (value == null) return "";
        return value.length() <= MAX_ERROR_BODY ? value : value.substring(0, MAX_ERROR_BODY);
    }
}
