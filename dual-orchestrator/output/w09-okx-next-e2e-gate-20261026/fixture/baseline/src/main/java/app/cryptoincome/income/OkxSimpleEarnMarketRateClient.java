package app.cryptoincome.income;

import app.cryptoincome.funding.infrastructure.okx.OkxPublicApiProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

@Component
public class OkxSimpleEarnMarketRateClient implements SimpleEarnMarketRateSource {
    public static final String ENDPOINT = "/api/v5/finance/savings/lending-rate-summary?ccy=USDT";
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final OkxPublicApiProperties properties;
    private final Clock clock;

    public OkxSimpleEarnMarketRateClient(@Qualifier("okxHttpClient") HttpClient client, ObjectMapper mapper,
                                         OkxPublicApiProperties properties, Clock clock) {
        this.client = client; this.mapper = mapper; this.properties = properties; this.clock = clock;
    }

    @Override public SimpleEarnMarketRate fetchUsdtFlexible() {
        HttpRequest request = HttpRequest.newBuilder(properties.baseUrl().resolve(ENDPOINT))
                .timeout(properties.requestTimeout()).header("Accept", "application/json").GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SimpleEarnSourceException("OKX 活期利率 HTTP 错误 " + response.statusCode(),
                        response.statusCode() == 429 || response.statusCode() >= 500, null);
            }
            return parse(response.body());
        } catch (HttpTimeoutException timeout) {
            throw new SimpleEarnSourceException("OKX 活期利率请求超时", true, timeout);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SimpleEarnSourceException("OKX 活期利率请求被中断", true, interrupted);
        } catch (IOException network) {
            throw new SimpleEarnSourceException("OKX 活期利率网络请求失败", true, network);
        }
    }

    SimpleEarnMarketRate parse(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            if (!"0".equals(text(root, "code"))) {
                throw new SimpleEarnSourceException("OKX 拒绝活期利率请求: " + root.path("msg").asText(), true, null);
            }
            JsonNode data = root.path("data");
            if (!data.isArray() || data.size() != 1) throw new IllegalArgumentException("USDT 活期利率必须恰好一条");
            JsonNode row = data.get(0);
            if (!"USDT".equals(text(row, "ccy"))) throw new IllegalArgumentException("返回币种不是 USDT");
            return new SimpleEarnMarketRate("USDT", decimal(row, "avgRate"), decimal(row, "preRate"),
                    decimal(row, "estRate"), clock.instant(), ENDPOINT);
        } catch (SimpleEarnSourceException known) {
            throw known;
        } catch (RuntimeException malformed) {
            throw new SimpleEarnSourceException("OKX 活期利率响应无效: " + malformed.getMessage(), false, malformed);
        }
    }
    private static BigDecimal decimal(JsonNode node, String field) { return new BigDecimal(text(node, field)); }
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException("缺少字段 " + field);
        return value.asText();
    }
}
