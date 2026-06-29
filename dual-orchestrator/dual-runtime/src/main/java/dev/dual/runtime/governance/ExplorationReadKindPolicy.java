package dev.dual.runtime.governance;

import java.util.Locale;

/**
 * Stage policy for classifying read kinds. File kind comes from the orchestrator
 * repo map; this class does not infer repository semantics from path suffixes.
 */
final class ExplorationReadKindPolicy {
    private ExplorationReadKindPolicy() {
    }

    static boolean isImplementationLevelRead(String stageId, String fileKind) {
        if (!"intake".equals(text(stageId))) {
            return false;
        }
        return switch (text(fileKind).toLowerCase(Locale.ROOT)) {
            case "source", "test" -> true;
            default -> false;
        };
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
