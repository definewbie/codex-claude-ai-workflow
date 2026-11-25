package app.cryptoincome.grid.infrastructure.okx;

import app.cryptoincome.funding.infrastructure.okx.OkxPublicApiProperties;
import app.cryptoincome.grid.domain.GridDuration;
import app.cryptoincome.grid.domain.GridParameterSnapshot;
import app.cryptoincome.grid.domain.GridParameterSource;
import app.cryptoincome.grid.domain.GridSourceException;
import app.cryptoincome.grid.domain.GridType;
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
public class OkxGridParameterClient implements GridParameterSource {
    public static final String ENDPOINT = "/api/v5/tradingBot/grid/ai-param";
    private static final int MAX_ERROR_BODY = 500;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OkxPublicApiProperties properties;
    private final Clock clock;

    public OkxGridParameterClient(
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
    public GridParameterSnapshot fetch(String instrumentId, GridDuration duration) {
        if (instrumentId == null || instrumentId.isBlank()) throw new IllegalArgumentException("instrumentId 不能为空");
        if (duration == null) throw new IllegalArgumentException("duration 不能为空");
        URI uri = properties.baseUrl().resolve(ENDPOINT
                + "?instId=" + URLEncoder.encode(instrumentId, StandardCharsets.UTF_8)
                + "&algoOrdType=grid&duration=" + duration.providerValue());
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(properties.requestTimeout())
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException timeout) {
            throw failure(GridSourceException.Category.TIMEOUT, null, true, "OKX 网格参数请求超时", timeout);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw failure(GridSourceException.Category.INTERRUPTED, null, true, "OKX 网格参数请求被中断", interrupted);
        } catch (IOException network) {
            throw failure(GridSourceException.Category.NETWORK, null, true, "OKX 网格参数网络请求失败", network);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;
            throw failure(GridSourceException.Category.HTTP_ERROR, Integer.toString(response.statusCode()), retryable,
                    "OKX 网格参数 HTTP 错误 " + response.statusCode() + ": " + bounded(response.body()), null);
        }
        return parse(response.body(), instrumentId, duration);
    }

    private GridParameterSnapshot parse(String body, String requestedInstrument, GridDuration requestedDuration) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String code = requiredText(root, "code");
            if (!"0".equals(code)) {
                throw failure(GridSourceException.Category.API_REJECTED, code, "50011".equals(code),
                        "OKX API 拒绝网格参数请求: " + bounded(root.path("msg").asText()), null);
            }
            JsonNode data = root.path("data");
            if (!data.isArray() || data.size() != 1) throw invalid("OKX 网格参数 data 必须恰有一项", null);
            JsonNode item = data.get(0);
            String instrumentId = requiredText(item, "instId");
            if (!requestedInstrument.equals(instrumentId)) throw invalid("OKX 返回了非请求标的: " + instrumentId, null);
            if (!"grid".equals(requiredText(item, "algoOrdType"))) throw invalid("OKX 返回的不是现货网格参数", null);
            GridDuration duration = GridDuration.fromProvider(requiredText(item, "duration"));
            if (duration != requestedDuration) throw invalid("OKX 返回了非请求期限: " + duration.providerValue(), null);
            GridType type = GridType.fromProvider(requiredText(item, "runType"));
            BigDecimal lower = decimal(item, "minPx");
            BigDecimal upper = decimal(item, "maxPx");
            int gridCount = Integer.parseInt(requiredText(item, "gridNum"));
            BigDecimal annualized = decimal(item, "annualizedRate");
            BigDecimal minProfit = decimal(item, "perMinProfitRate");
            BigDecimal maxProfit = decimal(item, "perMaxProfitRate");
            BigDecimal minimumInvestment = decimal(item, "minInvestment");
            String currency = requiredText(item, "ccy");
            String canonical = String.join("|", "OKX", instrumentId, duration.providerValue(), type.name(),
                    canonical(lower), canonical(upper), Integer.toString(gridCount), canonical(annualized),
                    canonical(minProfit), canonical(maxProfit), canonical(minimumInvestment), currency);
            Instant observedAt = clock.instant();
            return new GridParameterSnapshot(
                    "OKX", instrumentId, duration, type, lower, upper, gridCount, annualized,
                    minProfit, maxProfit, minimumInvestment, currency,
                    ENDPOINT, item.toString(), sha256(canonical), observedAt);
        } catch (GridSourceException known) {
            throw known;
        } catch (RuntimeException malformed) {
            throw invalid("OKX 网格参数响应字段无效: " + bounded(malformed.getMessage()), malformed);
        }
    }

    private static BigDecimal decimal(JsonNode node, String field) { return new BigDecimal(requiredText(node, field)); }

    private static String requiredText(JsonNode node, String field) {
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

    private static GridSourceException invalid(String message, Throwable cause) {
        return failure(GridSourceException.Category.INVALID_RESPONSE, null, false, message, cause);
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
