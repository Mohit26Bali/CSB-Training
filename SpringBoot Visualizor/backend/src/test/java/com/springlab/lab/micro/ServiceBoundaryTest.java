package com.springlab.lab.micro;

import com.springlab.lab.micro.core.ServiceName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Service boundaries, enforced on the source: a service package may not import another service's package, and a service's
 * repository may only mention the tables its service owns. Cross-service data access has to go through the contract and the bus.
 */
class ServiceBoundaryTest {

    private static final Path ROOT = Path.of("src/main/java/com/springlab/lab/micro");
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?com\\.springlab\\.lab\\.micro\\.(\\w+)", Pattern.MULTILINE);
    private static final Pattern TABLE = Pattern.compile("micro_lab_[a-z_]+");

    private static final Set<String> SERVICE_PACKAGES = Set.of("user", "product", "order", "inventory", "payment", "notification", "registry", "gateway");

    private static List<Path> javaFiles(String pkg) throws IOException {
        Path dir = ROOT.resolve(pkg);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
    }

    @Test
    void serviceSourceDirectoriesMatchTheServiceNames() {
        for (ServiceName s : ServiceName.values()) {
            assertThat(SERVICE_PACKAGES).contains(s.id());
        }
    }

    @Test
    void aServicePackageImportsOnlyCoreRunAndContractPackages() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String pkg : SERVICE_PACKAGES) {
            for (Path file : javaFiles(pkg)) {
                Matcher m = IMPORT.matcher(Files.readString(file));
                while (m.find()) {
                    String imported = m.group(1);
                    if (!Set.of("core", "run", "contract", pkg).contains(imported)) {
                        violations.add(file.getFileName() + " imports micro." + imported);
                    }
                }
            }
        }
        assertThat(violations).as("a service must not reach into another service or into the experiments").isEmpty();
    }

    @Test
    void aServiceMayNotTouchTheRunServiceOrControllerOrStore() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String pkg : SERVICE_PACKAGES) {
            for (Path file : javaFiles(pkg)) {
                String text = Files.readString(file);
                for (String forbidden : List.of("MicroRunService", "MicroRunStore", "LabMicroController", "RunContext;")) {
                    if (text.contains("import com.springlab.lab.micro.run." + forbidden) || (forbidden.equals("RunContext;") && text.contains(".run.RunContext;"))) {
                        violations.add(file.getFileName() + " uses " + forbidden);
                    }
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void eachServiceTouchesOnlyTheTablesItOwns() throws IOException {
        for (ServiceName service : ServiceName.values()) {
            Set<String> owned = Set.copyOf(service.ownedTables());
            for (Path file : javaFiles(service.id())) {
                Matcher m = TABLE.matcher(Files.readString(file));
                while (m.find()) {
                    assertThat(owned).as(file.getFileName() + " mentions " + m.group() + " which " + service.id() + " does not own").contains(m.group());
                }
            }
        }
    }

    @Test
    void everyTableIsOwnedByExactlyOneService() {
        List<String> all = new ArrayList<>();
        for (ServiceName s : ServiceName.values()) {
            all.addAll(s.ownedTables());
        }
        assertThat(all).doesNotHaveDuplicates();
    }

    @Test
    void experimentsAndContractsDoNotUseServiceInternalsOrJdbcDirectly() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String pkg : List.of("experiment", "contract")) {
            for (Path file : javaFiles(pkg)) {
                String text = Files.readString(file);
                Matcher m = IMPORT.matcher(text);
                while (m.find()) {
                    if (SERVICE_PACKAGES.contains(m.group(1))) {
                        violations.add(file.getFileName() + " imports the service package micro." + m.group(1));
                    }
                }
                if (text.contains("org.springframework.jdbc") || text.contains("javax.sql")) {
                    violations.add(file.getFileName() + " uses JDBC directly");
                }
            }
        }
        assertThat(violations).isEmpty();
    }
}
