package dev.dual.adapter.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class GitCommand {
    String run(Path workdir, String... args) throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command)
                .directory(workdir.toFile())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        var exit = process.waitFor();
        if (exit != 0) {
            throw new IOException("git command failed (" + exit + "): git " + String.join(" ", args) + "\n" + output);
        }
        return output;
    }
}
