package dev.dual.ledger.file;

import java.io.IOException;

public final class DeliveryMergePlanWriter {
    private final AtomicJsonWriter jsonWriter = new AtomicJsonWriter();

    public void write(WorkstreamPaths paths, DeliveryMergePlan plan) throws IOException {
        jsonWriter.write(paths.deliveryMergePlanJson(), plan);
    }
}
