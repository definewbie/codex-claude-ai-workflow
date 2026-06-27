package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AcpxProviderMetadataTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void resolvesGlmDefaultModelFromAcpxSessionDescription() {
        var metadata = AcpxProviderMetadata.fromJson(mapper.valueToTree(Map.of(
                "current_model_id", "default",
                "available_models", java.util.List.of(Map.of(
                        "id", "model",
                        "description", "Use the default model (currently glm-5.2[1m]) · bundled subscription"
                ))
        )));

        assertEquals("zhipu", metadata.providerId());
        assertEquals("glm-5.2", metadata.model());
        assertEquals(AcpxProviderMetadata.BILLING_SUBSCRIPTION_TOKENS, metadata.billingMode());
        assertEquals(AcpxProviderMetadata.SOURCE_ACPX_SESSION_JSON, metadata.source());
    }

    @Test
    void resolvesGlmFromNestedAcpxModelConfig() throws Exception {
        var metadata = AcpxProviderMetadata.fromJson(mapper.readTree("""
                {
                  "acpx": {
                    "current_model_id": "default",
                    "config_options": [{
                      "id": "model",
                      "currentValue": "default",
                      "options": [{
                        "description": "Use the default model (currently glm-5.2[1m]) · $5/$25 per Mtok",
                        "name": "Default (recommended)",
                        "value": "default"
                      }, {
                        "description": "Custom Opus model",
                        "name": "glm-5.2",
                        "value": "opus"
                      }]
                    }]
                  }
                }
                """));

        assertEquals("zhipu", metadata.providerId());
        assertEquals("glm-5.2", metadata.model());
        assertEquals(AcpxProviderMetadata.BILLING_SUBSCRIPTION_TOKENS, metadata.billingMode());
        assertEquals(AcpxProviderMetadata.SOURCE_ACPX_SESSION_JSON, metadata.source());
    }

    @Test
    void resolvesOpenAiModelFromCurrentModelId() {
        var metadata = AcpxProviderMetadata.fromJson(mapper.valueToTree(Map.of(
                "current_model_id", "gpt-5.5/high"
        )));

        assertEquals("openai", metadata.providerId());
        assertEquals("gpt-5.5/high", metadata.model());
        assertEquals(AcpxProviderMetadata.BILLING_METERED, metadata.billingMode());
        assertEquals(AcpxProviderMetadata.SOURCE_ACPX_SESSION_JSON, metadata.source());
    }
}
