package com.springlab.lab;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springlab.lab.catalog.LifecycleCatalog;
import com.springlab.lab.code.CodeReference;
import com.springlab.lab.trace.CodeRefIds;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards every source excerpt the Code Inspector shows. If SpringLab's source changes so that an excerpt no longer
 * matches the real file, this test fails and the references must be regenerated (see docs/tools/gen_code_refs.py).
 * No Spring context needed: pure file comparison.
 */
class CodeReferenceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static List<CodeReference> references;
    private static LifecycleCatalog.Catalog catalog;

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = new ClassPathResource("lab/code-references.json").getInputStream()) {
            references = MAPPER.readValue(in, new TypeReference<>() { });
        }
        try (InputStream in = new ClassPathResource("lab/lifecycle-catalog.json").getInputStream()) {
            catalog = MAPPER.readValue(in, LifecycleCatalog.Catalog.class);
        }
    }

    private static List<String> sourceLines(CodeReference ref) throws Exception {
        Path file = Path.of(ref.file()); // relative to the backend module, the working directory of `mvn test`
        assertThat(file).as("source file of %s", ref.id()).exists();
        return Files.readAllLines(file); // handles \n and \r\n
    }

    @Test
    void everyExcerptEqualsTheRealSourceLines() throws Exception {
        for (CodeReference ref : references) {
            List<String> lines = sourceLines(ref);
            assertThat(ref.startLine()).as("%s start", ref.id()).isBetween(1, lines.size());
            assertThat(ref.endLine()).as("%s end", ref.id()).isBetween(ref.startLine(), lines.size());
            String actual = String.join("\n", lines.subList(ref.startLine() - 1, ref.endLine()));
            assertThat(ref.excerpt()).as("excerpt of %s (%s:%d-%d) has drifted from the source", ref.id(), ref.file(),
                    ref.startLine(), ref.endLine()).isEqualTo(actual);
        }
    }

    @Test
    void everyNotePointsAtALineThatReallyContainsItsToken() throws Exception {
        for (CodeReference ref : references) {
            List<String> lines = sourceLines(ref);
            assertThat(ref.notes()).as("notes of %s", ref.id()).isNotEmpty();
            for (CodeReference.Note note : ref.notes()) {
                assertThat(note.line()).as("%s note line", ref.id()).isBetween(ref.startLine(), ref.endLine());
                assertThat(lines.get(note.line() - 1)).as("%s line %d must contain '%s'", ref.id(), note.line(),
                        note.token()).contains(note.token());
                assertThat(note.text()).isNotBlank();
            }
        }
    }

    @Test
    void referenceIdsAreUniqueAndCoverEveryIdTheAssemblerUses() {
        Set<String> ids = references.stream().map(CodeReference::id).collect(Collectors.toSet());
        assertThat(ids).hasSameSizeAs(references);
        assertThat(ids).containsAll(CodeRefIds.ALL);
    }

    @Test
    void everyReferencedFileIsRealSpringLabSourceNotIllustrative() {
        assertThat(references).allSatisfy(ref -> assertThat(ref.file())
                .matches("src/main/(java/com/springlab/.+\\.java|resources/application\\.yml)"));
    }

    @Test
    void catalogIsConsistent() {
        Set<String> targetIds = new HashSet<>();
        catalog.targets().forEach(t -> assertThat(targetIds.add(t.id())).as("duplicate target %s", t.id()).isTrue());
        Set<String> refIds = references.stream().map(CodeReference::id).collect(Collectors.toSet());
        Set<String> experimentIds = new HashSet<>();

        assertThat(catalog.targets()).allSatisfy(t -> {
            assertThat(t.method()).isEqualTo("GET"); // only side-effect-free requests may be traced
            assertThat(t.path()).startsWith("/api/");
            assertThat(t.expectation()).isNotEmpty();
        });
        assertThat(catalog.experiments().size()).isGreaterThanOrEqualTo(3);
        for (LifecycleCatalog.Experiment e : catalog.experiments()) {
            assertThat(experimentIds.add(e.id())).as("duplicate experiment %s", e.id()).isTrue();
            assertThat(e.kind()).isIn("LIVE", "SIMULATED");
            assertThat(e.consequence()).isNotBlank();
            assertThat(e.changes()).isNotBlank();
            if (e.codeRefId() != null) {
                assertThat(refIds).contains(e.codeRefId());
            }
            if (e.kind().equals("LIVE")) {
                assertThat(targetIds).contains(e.target());
                assertThat(e.variant()).isIn("AS_IS", "NO_TOKEN", "TAMPERED_TOKEN");
                assertThat(e.expectation()).isNotEmpty();
            } else {
                // SIMULATED experiments make no executable claims and must say where the real behaviour is documented.
                assertThat(e.target()).isNull();
                assertThat(e.expectation()).isNull();
                assertThat(e.realBehaviourReference()).isNotBlank();
            }
        }
        assertThat(catalog.experiments().stream().filter(e -> e.kind().equals("LIVE")).count()).isGreaterThanOrEqualTo(3);
        assertThat(catalog.experiments().stream().filter(e -> e.kind().equals("SIMULATED")).count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void expectationsOnlyUseKnownRoleKeys() {
        Set<String> allowed = Set.of("*", "STUDENT", "TEACHER", "ADMIN");
        for (LifecycleCatalog.Target t : catalog.targets()) {
            assertThat(allowed).containsAll(t.expectation().keySet());
        }
        for (LifecycleCatalog.Experiment e : catalog.experiments()) {
            Map<String, LifecycleCatalog.Expectation> ex = e.expectation();
            if (ex != null) {
                assertThat(allowed).containsAll(ex.keySet());
            }
        }
    }
}
