package com.springlab.lab.micro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Keeps the frontend honest. The Microservices Lab frontend validates its diagram, its mechanism classification and its What-If forms
 * against {@code frontend/src/learning/scenarios/microCatalog.fixture.json}. This test regenerates that file's content from the REAL
 * backend (every experiment run with its default What-If values) and fails when the checked-in file differs, so a backend change that the
 * frontend does not know about cannot go unnoticed.
 *
 * <p>To refresh the file after an intended backend change:
 * {@code mvn test -Dtest=MicroFrontendFixtureTest -Dmicro.fixture.write=true} (then commit the file).
 */
class MicroFrontendFixtureTest extends AbstractMicroLabTest {

    static final Path FIXTURE = Path.of("../frontend/src/learning/scenarios/microCatalog.fixture.json");

    @Test
    void theFrontendFixtureMatchesTheRealBackend() throws Exception {
        JsonNode generated = generate();
        if (Boolean.getBoolean("micro.fixture.write")) {
            Files.writeString(FIXTURE, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(generated) + "\n");
        }
        assertThat(Files.exists(FIXTURE)).as("the fixture file exists (generate it with -Dmicro.fixture.write=true)").isTrue();
        JsonNode checkedIn = objectMapper.readTree(Files.readString(FIXTURE));
        assertThat(checkedIn).as("microCatalog.fixture.json is stale: regenerate it with mvn test -Dtest=MicroFrontendFixtureTest -Dmicro.fixture.write=true").isEqualTo(generated);
    }

    private JsonNode generate() throws Exception {
        ObjectNode root = objectMapper.createObjectNode();

        ArrayNode services = root.putArray("services");
        for (JsonNode s : json(getAs("/api/lab/microservices/meta", student).andExpect(status().isOk())).get("services")) {
            ObjectNode out = services.addObject();
            out.set("id", s.get("id"));
            out.set("ownedTables", s.get("ownedTables"));
            out.set("databaseNode", s.get("databaseNode"));
        }
        ArrayNode topics = root.putArray("topics");
        for (JsonNode t : json(getAs("/api/lab/microservices/topics", student).andExpect(status().isOk()))) {
            topics.add(t.get("id").asText());
        }

        List<JsonNode> catalogue = new ArrayList<>();
        json(getAs("/api/lab/microservices/experiments", student).andExpect(status().isOk())).forEach(catalogue::add);
        catalogue.sort(Comparator.comparing(e -> e.get("id").asText()));

        ArrayNode experiments = root.putArray("experiments");
        for (JsonNode meta : catalogue) {
            ObjectNode out = experiments.addObject();
            out.set("id", meta.get("id"));
            out.set("topic", meta.get("topic"));
            out.set("diagram", meta.get("diagram"));
            out.set("whatIfs", meta.get("whatIfs"));

            JsonNode run = run(student, meta.get("id").asText(), "{}");
            JsonNode calls = run.get("result").get("calls");
            ArrayNode steps = out.putArray("steps");
            for (JsonNode s : run.get("result").get("steps")) {
                ObjectNode step = steps.addObject();
                step.set("id", s.get("id"));
                step.set("provenance", s.get("provenance"));
                step.set("nodes", s.get("nodes"));
                step.set("edgeFrom", s.get("edgeFrom"));
                step.set("edgeTo", s.get("edgeTo"));
                step.set("codeRefId", s.get("codeRefId"));
                ArrayNode callList = step.putArray("calls");
                for (JsonNode e : s.get("evidence")) {
                    String label = e.get("label").asText();
                    if (label.startsWith("Service call ")) {
                        int seq = Integer.parseInt(label.substring("Service call ".length()));
                        for (JsonNode c : calls) {
                            if (c.get("seq").asInt() == seq) {
                                callList.add(c.get("from").asText() + ">" + c.get("to").asText() + "." + c.get("operation").asText() + ":" + c.get("kind").asText()
                                        + ":" + c.get("outcome").asText());
                            }
                        }
                    }
                }
            }
        }
        return root;
    }
}
