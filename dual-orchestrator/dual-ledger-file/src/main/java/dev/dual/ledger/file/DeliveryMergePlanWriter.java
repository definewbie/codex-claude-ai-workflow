package dev.dual.ledger.file;

import java.io.IOException;

public final class DeliveryMergePlanWriter {
    private final AtomicJsonWriter jsonWriter;
    public DeliveryMergePlanWriter() { this.jsonWriter = new AtomicJsonWriter(); }
    public DeliveryMergePlanWriter(LedgerSession session) { this.jsonWriter = new AtomicJsonWriter(session); }

    public void write(WorkstreamPaths paths, DeliveryMergePlan plan) throws IOException {
        jsonWriter.write(paths.deliveryMergePlanJson(), plan);
    }
}
