package dev.dual.core.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderPricingCatalogTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void loadsWorkspacePricingAndMatchesWildcardModels() throws Exception {
        writePricing("""
                {
                  "schemaVersion": 1,
                  "models": [{
                    "providerId": "zhipu",
                    "model": "glm-*",
                    "currency": "TOKENS_ONLY",
                    "inputCacheHitPer1M": "0",
                    "inputCacheMissPer1M": "0",
                    "outputPer1M": "0",
                    "sourceUrl": "local"
                  }]
                }
                """);

        var catalog = ProviderPricingCatalog.load(tempDir, mapper);
        var price = catalog.priceFor("zhipu", "glm-5.2");

        assertTrue(price.isPresent());
        assertEquals("TOKENS_ONLY", price.get().currency());
        assertEquals("local", price.get().sourceUrl());
    }

    @Test
    void calculatesProviderCacheAwareCostFromOneCatalog() throws Exception {
        writePricing("""
                {
                  "schemaVersion": 1,
                  "models": [{
                    "providerId": "deepseek",
                    "model": "deepseek-pro-v4",
                    "currency": "CNY",
                    "inputCacheHitPer1M": "0.025",
                    "inputCacheMissPer1M": "3.00",
                    "outputPer1M": "6.00",
                    "cacheWritePer1M": "0"
                  }]
                }
                """);

        var price = ProviderPricingCatalog.load(tempDir, mapper)
                .priceFor("deepseek", "deepseek-pro-v4")
                .orElseThrow();

        assertEquals(
                0,
                new BigDecimal("0.423680").compareTo(price.cost(728704, 85606, 24774, 0))
        );
    }

    @Test
    void supportsConfiguredPricingPath() throws Exception {
        var configured = tempDir.resolve("custom-pricing.json");
        Files.writeString(configured, """
                {
                  "schemaVersion": 1,
                  "models": [{
                    "providerId": "openai",
                    "model": "chatgpt",
                    "currency": "TOKENS_ONLY",
                    "inputCacheHitPer1M": "0",
                    "inputCacheMissPer1M": "0",
                    "outputPer1M": "0"
                  }]
                }
                """);

        var catalog = ProviderPricingCatalog.load(
                tempDir,
                mapper,
                name -> "DUAL_PROVIDER_PRICING_PATH".equals(name) ? configured.toString() : ""
        );

        assertTrue(catalog.priceFor("openai", "chatgpt").isPresent());
    }

    @Test
    void loadsPackagedDefaultPricingWhenWorkspacePricingIsAbsent() {
        var catalog = ProviderPricingCatalog.load(tempDir, mapper, name -> "");

        assertTrue(catalog.priceFor("deepseek", "deepseek-pro-v4").isPresent());
        assertTrue(catalog.priceFor("deepseek", "deepseek-v4-pro").isPresent());
        assertTrue(catalog.priceFor("anthropic", "claude-sonnet-4.5").isPresent());
        assertTrue(catalog.priceFor("openai", "chatgpt").isPresent());
        assertTrue(catalog.priceFor("zhipu", "glm-5.2").isPresent());
        assertEquals("TOKENS_ONLY", catalog.priceFor("zhipu", "glm-5.2").orElseThrow().currency());
    }

    @Test
    void packagedDefaultPricingMatchesRepositoryExample() throws Exception {
        var example = Files.isRegularFile(Path.of("config/provider-pricing.example.json"))
                ? Path.of("config/provider-pricing.example.json")
                : Path.of("../config/provider-pricing.example.json");
        try (var packaged = ProviderPricingCatalog.class.getResourceAsStream(
                "/dev/dual/core/provider/provider-pricing.default.json")) {
            assertTrue(Files.isRegularFile(example));
            assertTrue(packaged != null);
            assertEquals(
                    mapper.readTree(example.toFile()),
                    mapper.readTree(packaged)
            );
        }
    }

    private void writePricing(String json) throws Exception {
        var agentDir = tempDir.resolve(".agent");
        Files.createDirectories(agentDir);
        Files.writeString(agentDir.resolve("provider-pricing.json"), json);
    }
}
