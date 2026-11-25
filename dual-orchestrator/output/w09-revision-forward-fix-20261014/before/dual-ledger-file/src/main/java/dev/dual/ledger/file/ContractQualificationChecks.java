package dev.dual.ledger.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

/** Machine-owned diagnostic values carried by existing decisions and execution completions.
 * These values never confer approval, dispatch or retry authority. */
public final class ContractQualificationChecks {
    public static final String FAMILY = "TaskContractProjectionVerifier";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private ContractQualificationChecks() {}

    public enum Result { PASSED, FAILED, NOT_CHECKED, UNAVAILABLE }
    public enum Coverage { COVERED, NOT_INVOKED }

    public record InputReference(String kind, String ref, String sha256, String eventId, long sequence) {
        public InputReference {
            requireText(kind); requireText(ref);
            if (!List.of("FILE", "ROUTE_EVENT", "MISSING").contains(kind)) throw new IllegalArgumentException("invalid input reference kind");
            eventId = eventId == null ? "" : eventId;
            sha256 = sha256 == null ? "" : sha256;
            if ("MISSING".equals(kind)) {
                if (!sha256.isEmpty()) throw new IllegalArgumentException("missing input cannot have a byte hash");
            } else requireSha(sha256);
            if ("ROUTE_EVENT".equals(kind) && (eventId.isBlank() || sequence < 0))
                throw new IllegalArgumentException("route input requires entry identity");
            if (!"ROUTE_EVENT".equals(kind) && (!eventId.isEmpty() || sequence != -1))
                throw new IllegalArgumentException("file input cannot masquerade as a route entry");
        }
    }

    public record Check(String ruleId, String ruleVersion, Result result, String explanation,
                        List<InputReference> inputRefs, Map<String, Object> inputValues, boolean comparable) {
        public Check {
            requireText(ruleId); requireSha(ruleVersion);
            if (result == null) throw new IllegalArgumentException("check result is required");
            explanation = explanation == null ? "" : explanation;
            inputRefs = List.copyOf(inputRefs);
            inputValues = immutableValues(inputValues);
            if ((result == Result.NOT_CHECKED || result == Result.UNAVAILABLE) && (comparable || explanation.isBlank()))
                throw new IllegalArgumentException("deferred/unavailable checks need an explanation and no comparison claim");
            if (comparable && inputValues.isEmpty() && inputRefs.isEmpty())
                throw new IllegalArgumentException("empty evidence is not comparable input");
        }
    }

    public record Decision(int schemaVersion, String family, Coverage familyCoverage, String gateNodeInstanceId,
                           String sourceNodeInstanceId, List<String> subjectRefs, String runtimeIdentitySha256,
                           List<Check> checks) {
        public Decision {
            if (schemaVersion != 1 || !FAMILY.equals(family) || familyCoverage == null)
                throw new IllegalArgumentException("invalid qualification decision schema");
            requireText(gateNodeInstanceId);
            sourceNodeInstanceId = sourceNodeInstanceId == null ? "" : sourceNodeInstanceId;
            subjectRefs = List.copyOf(subjectRefs); checks = List.copyOf(checks);
            requireSha(runtimeIdentitySha256);
            if (familyCoverage == Coverage.COVERED && (sourceNodeInstanceId.isBlank() || subjectRefs.isEmpty() || checks.isEmpty()))
                throw new IllegalArgumentException("covered decision needs its actual subject and checks");
            if (familyCoverage == Coverage.NOT_INVOKED && !checks.isEmpty())
                throw new IllegalArgumentException("not-invoked decision cannot claim checks");
        }
        public Map<String, Object> toMap() { return map(this); }
    }

    public record ContinuationFailure(int schemaVersion, String family, String status, String decisionRef,
                                      String decisionSha256, String action, String sourceNodeInstanceId,
                                      String targetNodeInstanceId, List<String> subjectRefs,
                                      List<String> targetInputRefs, Map<String, Object> targetInputTransition,
                                      String consumer, String phase, String failureCode,
                                      String runtimeIdentitySha256, Check failedCheck, String explanation) {
        public ContinuationFailure {
            if (schemaVersion != 1 || !FAMILY.equals(family)
                    || !List.of("PAIRED", "DIAGNOSTIC_INSUFFICIENT", "NO_RESOLVING_SOURCE").contains(status))
                throw new IllegalArgumentException("invalid continuation diagnostic schema");
            requireText(targetNodeInstanceId); requireText(consumer); requireText(phase); requireText(failureCode);
            requireSha(runtimeIdentitySha256);
            decisionRef = decisionRef == null ? "" : decisionRef;
            decisionSha256 = decisionSha256 == null ? "" : decisionSha256;
            action = action == null ? "" : action;
            sourceNodeInstanceId = sourceNodeInstanceId == null ? "" : sourceNodeInstanceId;
            explanation = explanation == null ? "" : explanation;
            subjectRefs = List.copyOf(subjectRefs); targetInputRefs = List.copyOf(targetInputRefs);
            targetInputTransition = immutableValues(targetInputTransition);
            if ("PAIRED".equals(status)) {
                requireText(decisionRef); requireSha(decisionSha256); requireText(action); requireText(sourceNodeInstanceId);
                if (failedCheck == null || failedCheck.result() != Result.FAILED) throw new IllegalArgumentException("pair needs the actual failed check");
            } else if (explanation.isBlank()) throw new IllegalArgumentException("diagnostic gap must be explicit");
        }
        public Map<String, Object> toMap() { return map(this); }
    }

    public static Decision decision(Object value) { return closed(value, Decision.class); }
    public static ContinuationFailure continuation(Object value) { return closed(value, ContinuationFailure.class); }

    public static Map<String, Object> values(Object... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("named input pairs required");
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) {
            var name = (String) pairs[i]; requireText(name);
            if (result.containsKey(name)) throw new IllegalArgumentException("duplicate input name");
            result.put(name, JSON.convertValue(pairs[i + 1], Object.class));
        }
        return immutableValues(result);
    }

    public static String identity(Object actualContent) {
        try {
            var mapper = JSON.copy().enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsBytes(actualContent)));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException invalid) {
            throw new IllegalArgumentException("cannot fix diagnostic content identity", invalid);
        }
    }

    private static <T> T closed(Object value, Class<T> type) {
        var decoded = JSON.convertValue(value, type);
        try {
            // JSON integer width is not a semantic difference after disk replay. Reparse both closed
            // values, while still rejecting omitted/extra fields and any changed diagnostic content.
            if (!JSON.readTree(JSON.writeValueAsBytes(decoded)).equals(JSON.readTree(JSON.writeValueAsBytes(value))))
                throw new IllegalArgumentException("qualification diagnostic is not a closed value");
        } catch (java.io.IOException invalid) { throw new IllegalArgumentException("invalid diagnostic JSON", invalid); }
        return decoded;
    }
    private static Map<String, Object> map(Object value) { return immutableValues(JSON.convertValue(value, MAP)); }
    @SuppressWarnings("unchecked")
    private static Object freeze(Object value) {
        if (value instanceof Map<?, ?> raw) {
            var copy = new LinkedHashMap<String, Object>();
            for (var item : raw.entrySet()) { var key = (String) item.getKey(); requireText(key); copy.put(key, freeze(item.getValue())); }
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) return Collections.unmodifiableList(list.stream().map(ContractQualificationChecks::freeze).toList());
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Number) return value;
        throw new IllegalArgumentException("diagnostic input is not a fixed JSON value");
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> immutableValues(Map<String, Object> source) { return (Map<String, Object>) freeze(source); }
    private static void requireText(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("diagnostic identity is required"); }
    private static void requireSha(String value) { if (value == null || !value.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("measured diagnostic SHA is required"); }
}
