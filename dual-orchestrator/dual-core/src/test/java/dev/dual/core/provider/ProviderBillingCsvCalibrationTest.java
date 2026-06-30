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
    void marksCostSummaryExportsAsCostOnlyInsteadOfPricingUnknown() {
        var report = ProviderBillingCsvCalibration.parse("""
                user_id,utc_date,model,wallet_type,cost,currency
                user,2026-06-01,deepseek-v4-pro,Paid,0.8136378000000000,CNY
                """, ProviderPricingCatalog.load(tempDir, new ObjectMapper(), name -> ""));

        assertEquals(1, report.rows().size());
        assertEquals("cost_summary_only", report.rows().get(0).consistency());
        assertEquals("cost_summary_only", report.consistency());
        assertEquals(0, new BigDecimal("0.813638").compareTo(report.exportedTotal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(report.calculatedTotal()));
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

    @Test
    void calibratesProviderAmountExportGroupedByTokenType() throws Exception {
        writePricing("""
                {
                  "schemaVersion": 1,
                  "models": [{
                    "providerId": "deepseek",
                    "model": "deepseek-v4-pro",
                    "currency": "CNY",
                    "inputCacheHitPer1M": "0.025",
                    "inputCacheMissPer1M": "3.00",
                    "outputPer1M": "6.00"
                  }, {
                    "providerId": "deepseek",
                    "model": "deepseek-v4-pro",
                    "currency": "USD",
                    "inputCacheHitPer1M": "0.003625",
                    "inputCacheMissPer1M": "0.435",
                    "outputPer1M": "0.87"
                  }]
                }
                """);
        var pricing = ProviderPricingCatalog.load(tempDir, new ObjectMapper(), name -> "");

        var report = ProviderBillingCsvCalibration.parse("""
                user_id,utc_date,model,api_key_name,api_key,type,price,amount
                user,2026-06-01,deepseek-v4-pro,key,sk,input_cache_hit_tokens,0.000000025,196992
                user,2026-06-01,deepseek-v4-pro,key,sk,input_cache_miss_tokens,0.000003,228427
                user,2026-06-01,deepseek-v4-pro,key,sk,request_count,,10
                user,2026-06-01,deepseek-v4-pro,key,sk,output_tokens,0.000006,20572
                """, pricing);

        assertEquals(1, report.rows().size());
        assertEquals("deepseek", report.rows().get(0).providerId());
        assertEquals("deepseek-v4-pro", report.rows().get(0).model());
        assertEquals("CNY", report.rows().get(0).currency());
        assertEquals(0, new BigDecimal("0.813638").compareTo(report.exportedTotal()));
        assertEquals(0, new BigDecimal("0.813638").compareTo(report.calculatedTotal()));
        assertEquals("matches_official_pricing", report.consistency());
    }

    private void writePricing(String json) throws Exception {
        var agentDir = tempDir.resolve(".agent");
        Files.createDirectories(agentDir);
        Files.writeString(agentDir.resolve("provider-pricing.json"), json);
    }
}
