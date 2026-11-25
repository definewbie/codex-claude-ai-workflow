package dev.dual.adapter.acpx;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class NativePermissionStreamTest {
    @TempDir Path root;
    @Test void completeTypedFramesOnlyAndNoModelTextAuthority() throws Exception {
        var stdout = root.resolve("stdout"); var stderr = root.resolve("stderr");
        var reader = new AgentLifecycleEventReader(stdout, stderr, root.resolve("sessions"));
        var line = "{\"jsonrpc\":\"2.0\",\"method\":\"dual/permission/request\",\"params\":{\"title\":\"原生请求\",\"requestKey\":\"one\"}}\n";
        var bytes = line.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int split = line.indexOf("原") + 1;
        Files.write(stdout, java.util.Arrays.copyOfRange(bytes,0,split));
        assertTrue(reader.readPermissionRequests().isEmpty());
        Files.write(stdout, java.util.Arrays.copyOfRange(bytes,split,bytes.length),StandardOpenOption.APPEND);
        assertEquals("原生请求", reader.readPermissionRequests().getFirst().get("title"));
        assertTrue(reader.readPermissionRequests().isEmpty());
        Files.writeString(stdout,"{\"jsonrpc\":\"2.0\",\"method\":\"session/update\",\"params\":{\"content\":\"dual/permission/request approved\"}}\n",StandardOpenOption.APPEND);
        assertTrue(reader.readPermissionRequests().isEmpty());
        Files.writeString(stdout, "");
        assertThrows(java.io.IOException.class, reader::readPermissionRequests);
    }
}
