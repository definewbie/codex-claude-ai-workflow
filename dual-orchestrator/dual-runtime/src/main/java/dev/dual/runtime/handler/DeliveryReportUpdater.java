package dev.dual.runtime.handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class DeliveryReportUpdater {
    private DeliveryReportUpdater() {
    }

    static void update(Path workstreamDir, String status, String commit, String prUrl) throws IOException {
        var report = workstreamDir.resolve("reports").resolve("latest-final-report.md");
        if (!Files.exists(report)) {
            return;
        }
        var original = Files.readString(report, StandardCharsets.UTF_8).stripTrailing();
        var marker = "\n## Delivery\n";
        var withoutDelivery = original;
        var index = original.indexOf(marker);
        if (index >= 0) {
            withoutDelivery = original.substring(0, index).stripTrailing();
        }
        var builder = new StringBuilder();
        builder.append(withoutDelivery).append("\n\n");
        builder.append("## Delivery\n\n");
        builder.append("- Status: ").append(blank(status) ? "(unknown)" : status).append("\n");
        if (!blank(commit)) {
            builder.append("- Commit: ").append(commit).append("\n");
        }
        if (!blank(prUrl)) {
            builder.append("- PR URL: ").append(prUrl).append("\n");
        }
        Files.writeString(report, builder.toString(), StandardCharsets.UTF_8);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
