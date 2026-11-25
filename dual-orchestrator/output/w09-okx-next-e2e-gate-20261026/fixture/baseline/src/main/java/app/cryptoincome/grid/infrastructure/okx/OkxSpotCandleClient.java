package app.cryptoincome.grid.infrastructure.okx;

import app.cryptoincome.funding.infrastructure.okx.OkxPublicApiProperties;
import app.cryptoincome.grid.domain.CandleBar;
import app.cryptoincome.grid.domain.GridSourceException;
import app.cryptoincome.grid.domain.SpotCandle;
import app.cryptoincome.grid.domain.SpotCandlePageSource;
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
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public class OkxSpotCandleClient implements SpotCandlePageSource {
    public static final String ENDPOINT = "/api/v5/market/history-candles";
    private static final int MAX_ERROR_BODY = 500;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OkxPublicApiProperties properties;
    private final Clock clock;

    public OkxSpotCandleClient(
            @Qualifier("okxHttpClient") HttpClient httpClient,
            ObjectMapper objectMapper,
            OkxPublicApiProperties properties,
            Clock clock
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public List<SpotCandle> fetchPage(String instrumentId, CandleBar bar, int limit, Instant after) {
        if (instrumentId == null || instrumentId.isBlank()) throw new IllegalArgumentException("instrumentId 不能为空");
        if (bar == null) throw new IllegalArgumentException("bar 不能为空");
        if (limit < 1 || limit > 300) throw new IllegalArgumentException("limit 必须在 1 到 300 之间");
        if (after == null || after.getNano() % 1_000_000 != 0) {
            throw new IllegalArgumentException("after 必须是毫秒精度时间");
        }
        URI uri = properties.baseUrl().resolve(ENDPOINT
                + "?instId=" + URLEncoder.encode(instrumentId, StandardCharsets.UTF_8)
                + "&bar=" + bar.providerValue()
                + "&limit=" + limit
                + "&after=" + after.toEpochMilli());
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(properties.requestTimeout()).header("Accept", "application/json").GET().build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException timeout) {
            throw failure(GridSourceException.Category.TIMEOUT, null, true, "OKX K 线请求超时", timeout);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw failure(GridSourceException.Category.INTERRUPTED, null, true, "OKX K 线请求被中断", interrupted);
        } catch (IOException network) {
            throw failure(GridSourceException.Category.NETWORK, null, true, "OKX K 线网络请求失败", network);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;
            throw failure(GridSourceException.Category.HTTP_ERROR, Integer.toString(response.statusCode()), retryable,
                    "OKX K 线 HTTP 错误 " + response.statusCode() + ": " + bounded(response.body()), null);
        }
        return parse(response.body(), instrumentId, bar);
    }

    private List<SpotCandle> parse(String body, String instrumentId, CandleBar bar) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String code = text(root, "code");
            if (!"0".equals(code)) {
                throw failure(GridSourceException.Category.API_REJECTED, code, "50011".equals(code),
                        "OKX API 拒绝 K 线请求: " + bounded(root.path("msg").asText()), null);
            }
            JsonNode data = root.path("data");
            if (!data.isArray()) throw new IllegalArgumentException("data 不是数组");
            Instant observedAt = clock.instant();
            List<SpotCandle> result = new ArrayList<>(data.size());
            for (JsonNode row : data) {
                if (!row.isArray() || row.size() != 9) throw new IllegalArgumentException("K 线数组必须包含 9 个字段");
                Instant openTime = Instant.ofEpochMilli(Long.parseLong(arrayText(row, 0)));
                BigDecimal open = decimal(row, 1);
                BigDecimal high = decimal(row, 2);
                BigDecimal low = decimal(row, 3);
                BigDecimal close = decimal(row, 4);
                BigDecimal baseVolume = decimal(row, 5);
                BigDecimal quoteVolume = decimal(row, 7);
                String confirm = arrayText(row, 8);
                if (!"0".equals(confirm) && !"1".equals(confirm)) {
                    throw new IllegalArgumentException("未知 K 线 confirm: " + confirm);
                }
                String canonical = String.join("|", "OKX", instrumentId, bar.providerValue(),
                        Long.toString(openTime.toEpochMilli()), canonical(open), canonical(high),
                        canonical(low), canonical(close), canonical(baseVolume), canonical(quoteVolume), confirm);
                result.add(new SpotCandle(
                        "OKX", instrumentId, bar, openTime, open, high, low, close,
                        baseVolume, quoteVolume, "1".equals(confirm), ENDPOINT, row.toString(),
                        sha256(canonical), observedAt));
            }
            return List.copyOf(result);
        } catch (GridSourceException known) {
            throw known;
        } catch (RuntimeException malformed) {
            throw failure(GridSourceException.Category.INVALID_RESPONSE, null, false,
                    "OKX K 线响应字段无效: " + bounded(malformed.getMessage()), malformed);
        }
    }

    private static BigDecimal decimal(JsonNode row, int index) { return new BigDecimal(arrayText(row, index)); }

    private static String arrayText(JsonNode row, int index) {
        JsonNode value = row.get(index);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("K 线字段 " + index + " 缺失");
        }
        return value.asText();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("缺少文本字段 " + field);
        }
        return value.asText();
    }

    private static String canonical(BigDecimal value) {
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

    private static GridSourceException failure(GridSourceException.Category category, String providerCode,
                                               boolean retryable, String message, Throwable cause) {
        return new GridSourceException(category, providerCode, retryable, message, cause);
    }

    private static String bounded(String value) {
        if (value == null) return "";
        return value.length() <= MAX_ERROR_BODY ? value : value.substring(0, MAX_ERROR_BODY);
    }
}
