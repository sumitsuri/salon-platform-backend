package com.salonplatform.aigateway;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway is meant to become its own library, and later its own service. This keeps that door open: nothing in
 * the module may depend on the rest of the application.
 */
class ModuleIndependenceTest {

    private static final Path ROOT = Path.of("src/main/java/com/salonplatform/aigateway");

    @Test
    void theGatewayImportsNothingFromTheRestOfTheApplication() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(ROOT)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    String t = line.trim();
                    boolean importsApp = t.startsWith("import com.salonplatform.") && !t.startsWith("import com.salonplatform.aigateway.");
                    boolean usesApp = t.contains("com.salonplatform.") && !t.contains("com.salonplatform.aigateway") && !t.startsWith("package ")
                            && !t.startsWith("*") && !t.startsWith("//");
                    if (importsApp || usesApp) violations.add(file.getFileName() + ": " + t);
                }
            }
        }
        assertThat(violations).as("the AI gateway must not depend on application code").isEmpty();
    }

    @Test
    void theModuleSourceActuallyExists() {
        assertThat(Files.isDirectory(ROOT)).isTrue();
    }
}
