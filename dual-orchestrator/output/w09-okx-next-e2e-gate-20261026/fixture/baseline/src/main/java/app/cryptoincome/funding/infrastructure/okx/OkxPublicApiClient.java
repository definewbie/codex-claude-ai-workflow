package app.cryptoincome.funding.infrastructure.okx;

import app.cryptoincome.funding.domain.DerivativeInstrumentType;
import app.cryptoincome.funding.domain.FundingFormulaType;
import app.cryptoincome.funding.domain.FundingRateHistoryPager;
import app.cryptoincome.funding.domain.FundingRateHistorySource;
import app.cryptoincome.funding.domain.FundingRateObservation;
import app.cryptoincome.funding.domain.FundingRateSourceException;
import app.cryptoincome.funding.domain.FundingSettlementMethod;
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
public class OkxPublicApiClient implements FundingRateHistorySource, FundingRateHistoryPager {
    static final String ENDPOINT = "/api/v5/public/funding-rate-history";
    private static final int MAX_ERROR_BODY = 500;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OkxPublicApiProperties properties;
    private final Clock clock;

    public OkxPublicApiClient(
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
    public List<FundingRateObservation> fetchLatest(String instrumentId, int limit) {
        requireInstrument(instrumentId);
        requireLimit(limit);
        URI uri = buildUri(instrumentId, limit, null);
        return get(uri, instrumentId);
    }

    /** after 语义: 请求该 fundingTime 毫秒值之前(更旧)的数据, 用于历史游标回补。 */
    @Override
    public List<FundingRateObservation> fetchPage(String instrumentId, int limit, Instant after) {
        requireInstrument(instrumentId);
        requireLimit(limit);
        if (after == null) {
            throw new IllegalArgumentException("after 游标不能为空");
        }
        if (after.getNano() % 1_000_000 != 0) {
            throw new IllegalArgumentException("after 游标精度不能低于 1 毫秒");
        }
        URI uri = buildUri(instrumentId, limit, after);
        return get(uri, instrumentId);
    }

    private URI buildUri(String instrumentId, int limit, Instant after) {
        StringBuilder query = new StringBuilder(ENDPOINT)
                .append("?instId=").append(URLEncoder.encode(instrumentId, StandardCharsets.UTF_8))
                .append("&limit=").append(limit);
        if (after != null) {
            query.append("&after=").append(after.toEpochMilli());
        }
        return properties.baseUrl().resolve(query.toString());
    }

    private List<FundingRateObservation> get(URI uri, String requestedInstrument) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(properties.requestTimeout())
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException timeout) {
            throw failure(FundingRateSourceException.Category.TIMEOUT, null, true, "OKX 请求超时", timeout);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw failure(FundingRateSourceException.Category.INTERRUPTED, null, true, "OKX 请求被中断", interrupted);
        } catch (IOException network) {
            throw failure(FundingRateSourceException.Category.NETWORK, null, true, "OKX 网络请求失败", network);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;
            throw failure(FundingRateSourceException.Category.HTTP_ERROR,
                    Integer.toString(response.statusCode()), retryable,
                    "OKX HTTP 错误 " + response.statusCode() + ": " + bounded(response.body()), null);
        }
        return parse(response.body(), requestedInstrument);
    }

    private static void requireInstrument(String instrumentId) {
        if (instrumentId == null || instrumentId.isBlank()) {
            throw new IllegalArgumentException("instrumentId 不能为空");
        }
    }

    private static void requireLimit(int limit) {
        if (limit < 1 || limit > 400) {
            throw new IllegalArgumentException("limit 必须在 1 到 400 之间");
        }
    }

    private List<FundingRateObservation> parse(String body, String requestedInstrument) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String code = requiredText(root, "code");
            if (!"0".equals(code)) {
                throw failure(FundingRateSourceException.Category.API_REJECTED, code, "50011".equals(code),
                        "OKX API 拒绝请求: " + bounded(root.path("msg").asText()), null);
            }
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                throw invalid("OKX data 不是数组", null);
            }
            Instant observedAt = clock.instant();
            List<FundingRateObservation> result = new ArrayList<>(data.size());
            for (JsonNode item : data) {
                result.add(mapItem(item, requestedInstrument, observedAt));
            }
            return List.copyOf(result);
        } catch (FundingRateSourceException known) {
            throw known;
        } catch (RuntimeException malformed) {
            throw invalid("OKX 响应字段无效: " + bounded(malformed.getMessage()), malformed);
        }
    }

    private FundingRateObservation mapItem(JsonNode item, String requestedInstrument, Instant observedAt) {
        String instrumentId = requiredText(item, "instId");
        if (!requestedInstrument.equals(instrumentId)) {
            throw invalid("OKX 返回了非请求标的: " + instrumentId, null);
        }
        String instrumentType = requiredText(item, "instType");
        String formulaType = requiredText(item, "formulaType");
        String method = requiredText(item, "method");
        BigDecimal predicted = new BigDecimal(requiredText(item, "fundingRate"));
        BigDecimal realized = new BigDecimal(requiredText(item, "realizedRate"));
        Instant fundingTime = Instant.ofEpochMilli(Long.parseLong(requiredText(item, "fundingTime")));
        String canonical = String.join("|", "OKX", instrumentType, instrumentId, formulaType,
                canonicalDecimal(predicted), canonicalDecimal(realized),
                Long.toString(fundingTime.toEpochMilli()), method);
        return new FundingRateObservation(
                "OKX", parseInstrumentType(instrumentType), instrumentId, parseFormulaType(formulaType),
                predicted, realized, fundingTime, parseMethod(method), ENDPOINT, item.toString(),
                sha256(canonical), observedAt);
    }

    private static DerivativeInstrumentType parseInstrumentType(String value) {
        return switch (value) {
            case "SWAP" -> DerivativeInstrumentType.SWAP;
            case "FUTURES" -> DerivativeInstrumentType.FUTURES;
            default -> throw new IllegalArgumentException("未知 instType: " + value);
        };
    }

    private static FundingFormulaType parseFormulaType(String value) {
        return switch (value) {
            case "noRate" -> FundingFormulaType.NO_RATE;
            case "withRate" -> FundingFormulaType.WITH_RATE;
            default -> throw new IllegalArgumentException("未知 formulaType: " + value);
        };
    }

    private static FundingSettlementMethod parseMethod(String value) {
        return switch (value) {
            case "current_period" -> FundingSettlementMethod.CURRENT_PERIOD;
            case "next_period" -> FundingSettlementMethod.NEXT_PERIOD;
            default -> throw new IllegalArgumentException("未知 method: " + value);
        };
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("缺少文本字段 " + field);
        }
        return value.asText();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JDK 缺少 SHA-256", impossible);
        }
    }

    private static String canonicalDecimal(BigDecimal value) {
        if (value.signum() == 0) return "0";
        return value.stripTrailingZeros().toPlainString();
    }

    private static FundingRateSourceException invalid(String message, Throwable cause) {
        return failure(FundingRateSourceException.Category.INVALID_RESPONSE, null, false, message, cause);
    }

    private static FundingRateSourceException failure(FundingRateSourceException.Category category,
                                                       String providerCode, boolean retryable,
                                                       String message, Throwable cause) {
        return new FundingRateSourceException(category, providerCode, retryable, message, cause);
    }

    private static String bounded(String value) {
        if (value == null) return "";
        return value.length() <= MAX_ERROR_BODY ? value : value.substring(0, MAX_ERROR_BODY);
    }
}
