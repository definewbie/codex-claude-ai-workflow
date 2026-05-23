package dev.dual.cli;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import picocli.CommandLine;

@SpringBootApplication
public class DualCtlApplication {
    public static void main(String[] args) {
        System.exit(new SpringApplicationBuilder(DualCtlApplication.class)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .run(args)
                .getBean(CommandLineRunner.class)
                instanceof ExitCodeRunner runner ? runner.exitCode() : 0);
    }

    @Bean
    CommandLineRunner commandLineRunner(DualCtlCommand command) {
        return new ExitCodeRunner(new CommandLine(command), command);
    }

    static final class ExitCodeRunner implements CommandLineRunner {
        private final CommandLine commandLine;
        private final DualCtlCommand command;
        private int exitCode;

        ExitCodeRunner(CommandLine commandLine, DualCtlCommand command) {
            this.commandLine = commandLine;
            this.command = command;
        }

        @Override
        public void run(String... args) {
            exitCode = commandLine.execute(args);
        }

        int exitCode() {
            return exitCode;
        }
    }
}
