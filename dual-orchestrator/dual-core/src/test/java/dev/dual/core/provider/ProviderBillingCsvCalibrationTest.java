package dev.dual.core.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderBillingCsvCalibrationTest {
    @TempDir
    Path tempDir;

    @Test
    void calibratesDeepSeekBillingExportWithOfficialPricingCatalog() throws Exception {
        writePricing("""
                {
                  "schemaVersion": 1,
                  "models": [{
                    "providerId": "deepseek",
                    "model": "deepseek-pro-v4",
                    "currency": "CNY",
                    "inputCacheHitPer1M": "0.025",
                    "inputCacheMissPer1M": "3.00",
                    "outputPer1M": "6.00"
                  }]
                }
                """);
        var pricing = ProviderPricingCatalog.load(tempDir, new ObjectMapper(), name -> "");

        var report = ProviderBillingCsvCalibration.parse("""
                provider_id,model,input_cache_hit_tokens,input_cache_miss_tokens,output_tokens,cache_write_tokens,amount,currency
                deepseek,deepseek-pro-v4,728704,85606,24774,0,0.423680,CNY
                """, pricing);

        assertEquals(1, report.rows().size());
        assertEquals(0, new BigDecimal("0.423680").compareTo(report.calculatedTotal()));
        assertEquals(0, new BigDecimal("0.423680").compareTo(report.exportedTotal()));
        assertEquals("matches_official_pricing", report.consistency());
        assertEquals("matches_official_pricing", report.rows().get(0).consistency());
    }

    @Test
    void marksUnknownProviderRowsWithoutInventingCost() {
        var report = ProviderBillingCsvCalibration.parse("""
                provider_id,model,input_cache_hit_tokens,input_cache_miss_tokens,output_tokens,amount,currency
                unknown,model-x,10,20,30,1.23,CNY
                """, ProviderPricingCatalog.load(tempDir, new ObjectMapper(), name -> ""));

        assertEquals(1, report.rows().size());
        assertEquals("pricing_unknown", report.rows().get(0).consistency());
        assertEquals(0, BigDecimal.ZERO.compareTo(report.calculatedTotal()));
        assertEquals(0, new BigDecimal("1.230000").compareTo(report.exportedTotal()));
    }

    @Test
    void acceptsCommonHeaderAliases() throws Exception {
        writePricing("""
                {
                  "schemaVersion": 1,
                  "models": [{
                    "providerId": "deepseek",
                    "model": "deepseek-pro-v4",
                    "currency": "CNY",
                    "inputCacheHitPer1M": "0.025",
                    "inputCacheMissPer1M": "3.00",
                    "outputPer1M": "6.00"
                  }]
                }
                """);
        var pricing = ProviderPricingCatalog.load(tempDir, new ObjectMapper(), name -> "");

        var report = ProviderBillingCsvCalibration.parse("""
                provider,model,cached_read_tokens,input_tokens,completion_tokens,cost,currency
                deepseek,deepseek-pro-v4,728704,85606,24774,0.423680,CNY
                """, pricing);

        assertEquals("matches_official_pricing", report.consistency());
    }

    private void writePricing(String json) throws Exception {
        var agentDir = tempDir.resolve(".agent");
        Files.createDirectories(agentDir);
        Files.writeString(agentDir.resolve("provider-pricing.json"), json);
    }
}
