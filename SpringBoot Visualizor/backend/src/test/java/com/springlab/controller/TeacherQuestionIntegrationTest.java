package com.springlab.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.springlab.entity.Attempt;
import com.springlab.entity.Difficulty;
import com.springlab.entity.Question;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherQuestionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EntityManager entityManager;

    // ---------------------------------------------------------------- helpers

    private ResultActions as(User user, MockHttpServletRequestBuilder request) throws Exception {
        if (user != null) {
            request.header("Authorization", bearer(user));
        }
        return mockMvc.perform(request);
    }

    private long topicId(String slug) {
        return topicRepository.findBySlug(slug).orElseThrow().getId();
    }

    /** Builds a request body. Options: texts, with the one at correctIndex marked correct. */
    private ObjectNode questionBody(String text, int correctIndex, String... optionTexts) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("text", text);
        body.put("explanation", "Because " + text);
        body.put("topicId", topicId("jwt-security"));
        body.put("difficulty", "MEDIUM");
        ArrayNode options = body.putArray("options");
        for (int i = 0; i < optionTexts.length; i++) {
            options.addObject().put("text", optionTexts[i]).put("correct", i == correctIndex);
        }
        return body;
    }

    private ObjectNode validBody(String text) {
        return questionBody(text, 1, "A", "B", "C", "D");
    }

    private ResultActions create(User user, ObjectNode body) throws Exception {
        return as(user, post("/api/teacher/questions").contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private JsonNode created(User user, ObjectNode body) throws Exception {
        MvcResult result = create(user, body).andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private ResultActions update(User user, long id, ObjectNode body) throws Exception {
        return as(user, put("/api/teacher/questions/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }

    private ResultActions setActive(User user, long id, boolean active) throws Exception {
        return as(user, patch("/api/teacher/questions/" + id + "/active").contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":" + active + "}"));
    }

    // ---------------------------------------------------------------- authorization (backend, not UI)

    @Test
    void everyTeacherEndpointRejectsAnonymousAndStudentCallers() throws Exception {
        User student = createUser("sam", Role.STUDENT);
        User teacher = createUser("tina", Role.TEACHER);
        Question q = createQuestion("jwt-security", Difficulty.EASY, teacher, "existing", 4, 1);
        long id = q.getId();
        String body = validBody("x").toString();

        List<Supplier<MockHttpServletRequestBuilder>> requests = List.of(
                () -> get("/api/teacher/questions"),
                () -> get("/api/teacher/questions/" + id),
                () -> get("/api/teacher/questions/" + id + "/preview"),
                () -> post("/api/teacher/questions").contentType(MediaType.APPLICATION_JSON).content(body),
                () -> put("/api/teacher/questions/" + id).contentType(MediaType.APPLICATION_JSON).content(body),
                () -> patch("/api/teacher/questions/" + id + "/active").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"));
        for (Supplier<MockHttpServletRequestBuilder> request : requests) {
            as(null, request.get()).andExpect(status().isUnauthorized());
            as(student, request.get()).andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        // Nothing the rejected students/anonymous callers attempted had any effect.
        assertThat(questionRepository.count()).isEqualTo(1);
        assertThat(questionRepository.findById(id).orElseThrow().isActive()).isTrue();
    }

    @Test
    void teachersAndAdminsCanUseTheEndpoints() throws Exception {
        User teacher = createUser("tina", Role.TEACHER);
        User admin = createUser("root", Role.ADMIN);

        as(teacher, get("/api/teacher/questions")).andExpect(status().isOk());
        as(admin, get("/api/teacher/questions")).andExpect(status().isOk());
        created(teacher, validBody("by teacher"));
        created(admin, validBody("by admin"));
    }

    @Test
    void demotedTeacherLosesAccessImmediately() throws Exception {
        User teacher = createUser("tina", Role.TEACHER);
        String token = bearer(teacher);
        mockMvc.perform(get("/api/teacher/questions").header("Authorization", token)).andExpect(status().isOk());

        teacher.setRole(Role.STUDENT);
        userRepository.saveAndFlush(teacher);

        mockMvc.perform(get("/api/teacher/questions").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- create / validate

    @Test
    void createsAQuestionWithOptionsExplanationTopicAndDifficulty() throws Exception {
        User teacher = createUser("tina", Role.TEACHER);

        MvcResult result = create(teacher, validBody("What is a JWT?"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/teacher/questions/")))
                .andExpect(jsonPath("$.text").value("What is a JWT?"))
                .andExpect(jsonPath("$.explanation").value("Because What is a JWT?"))
                .andExpect(jsonPath("$.topicSlug").value("jwt-security"))
                .andExpect(jsonPath("$.difficulty").value("MEDIUM"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdBy").value("tina"))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.options.length()").value(4))
                .andExpect(jsonPath("$.options[1].correct").value(true))
                .andExpect(jsonPath("$.options[0].correct").value(false))
                .andReturn();

        long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        entityManager.flush();
        entityManager.clear();
        Question stored = questionRepository.findDetailById(id).orElseThrow();
        assertThat(stored.getCreatedBy().getUsername()).isEqualTo("tina");
        assertThat(stored.getOptions()).extracting("text").containsExactly("A", "B", "C", "D");
        assertThat(stored.getOptions()).extracting("correct").containsExactly(false, true, false, false);
        assertThat(stored.getVersion()).isNotNull();
    }

    @Test
    void rejectsInvalidQuestions() throws Exception {
        User teacher = createUser("tina", Role.TEACHER);

        // exactly one correct option is required
        create(teacher, questionBody("none correct", -1, "A", "B")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.exactlyOneCorrectOption").exists());
        ObjectNode two = questionBody("two correct", 0, "A", "B", "C");
        ((ObjectNode) two.get("options").get(1)).put("correct", true);
        create(teacher, two).andExpect(status().isBadRequest());
        // option count 2..6
        create(teacher, questionBody("one option", 0, "A")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.options").exists());
        create(teacher, questionBody("seven", 0, "1", "2", "3", "4", "5", "6", "7")).andExpect(status().isBadRequest());
        // blank text / explanation / option text, missing topic or difficulty
        create(teacher, validBody("  ")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.text").exists());
        create(teacher, validBody("ok").put("explanation", "")).andExpect(status().isBadRequest());
        create(teacher, questionBody("blank option", 0, "A", " ")).andExpect(status().isBadRequest());
        create(teacher, validBody("ok").putNull("topicId")).andExpect(status().isBadRequest());
        create(teacher, validBody("ok").putNull("difficulty")).andExpect(status().isBadRequest());
        create(teacher, validBody("ok").put("difficulty", "IMPOSSIBLE")).andExpect(status().isBadRequest());
        // unknown topic, duplicate option texts (case-insensitive), over-long text
        create(teacher, validBody("ok").put("topicId", 999999999L)).andExpect(status().isBadRequest());
        create(teacher, questionBody("dup", 0, "Same", "same", "Other")).andExpect(status().isBadRequest());
        create(teacher, validBody("x".repeat(2001))).andExpect(status().isBadRequest());

        assertThat(questionRepository.count()).isZero();
    }

    // ---------------------------------------------------------------- update / option management / ownership

    @Test
    void ownerEditsTextOptionsTopicAndDifficultyInPlace() throws Exception {
        User teacher = createUser("tina", Role.TEACHER);
        JsonNode original = created(teacher, validBody("original"));
        long id = original.get("id").asLong();
        long keptOptionId = original.get("options").get(2).get("id").asLong();
        long removedOptionId = original.get("options").get(3).get("id").asLong();

        // keep option "C" (edited, now correct), drop "A"/"B"/"D" except "C", add a brand new option
        ObjectNode body = objectMapper.createObjectNode();
        body.put("text", "edited text").put("explanation", "edited explanation")
                .put("topicId", topicId("jpa-hibernate")).put("difficulty", "HARD")
                .put("version", original.get("version").asLong());
        ArrayNode options = body.putArray("options");
        options.addObject().put("id", keptOptionId).put("text", "C edited").put("correct", true);
        options.addObject().put("text", "brand new").put("correct", false);

        update(teacher, id, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("edited text"))
                .andExpect(jsonPath("$.topicSlug").value("jpa-hibernate"))
                .andExpect(jsonPath("$.difficulty").value("HARD"))
                .andExpect(jsonPath("$.options.length()").value(2))
                .andExpect(jsonPath("$.options[0].id").value(keptOptionId))
                .andExpect(jsonPath("$.options[0].text").value("C edited"))
                .andExpect(jsonPath("$.options[0].correct").value(true))
                .andExpect(jsonPath("$.options[1].text").value("brand new"))
                .andExpect(jsonPath("$.version").value(original.get("version").asLong() + 1));

        entityManager.flush();
        entityManager.clear();
        Question stored = questionRepository.findDetailById(id).orElseThrow();
        assertThat(stored.getOptions()).hasSize(2);
        assertThat(stored.getOptions()).extracting("id").doesNotContain(removedOptionId);
    }

    @Test
    void updateRequiresTheCurrentVersionAndRejectsStaleOnes() throws Exception {
        User teacher = createUser("tina", Role.TEACHER);
        JsonNode original = created(teacher, validBody("original"));
        long id = original.get("id").asLong();
        long version = original.get("version").asLong();

        update(teacher, id, validBody("no version")).andExpect(status().isBadRequest());
        update(teacher, id, validBody("first edit").put("version", version)).andExpect(status().isOk());
        // Second editor still holds the old version: must not overwrite the first edit.
        update(teacher, id, validBody("stale edit").put("version", version)).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        entityManager.flush();
        entityManager.clear();
        assertThat(questionRepository.findById(id).orElseThrow().getText()).isEqualTo("first edit");
    }

    @Test
    void editingOnlyTheOptionsStillBumpsTheVersionSoStaleEditsAreRejected() throws Exception {
        // Regression: changes to the inverse (mappedBy) options collection alone do not dirty the question row.
        User teacher = createUser("tina", Role.TEACHER);
        JsonNode original = created(teacher, validBody("unchanged text"));
        long id = original.get("id").asLong();
        long version = original.get("version").asLong();

        // Same text/explanation/topic/difficulty as before; only option "D" is renamed.
        ObjectNode optionsOnly = validBody("unchanged text").put("version", version);
        for (int i = 0; i < 4; i++) {
            ((ObjectNode) optionsOnly.get("options").get(i)).put("id", original.get("options").get(i).get("id").asLong());
        }
        ((ObjectNode) optionsOnly.get("options").get(3)).put("text", "D renamed");
        optionsOnly.put("explanation", original.get("explanation").asText());

        update(teacher, id, optionsOnly).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(version + 1))
                .andExpect(jsonPath("$.options[3].text").value("D renamed"));
        // A second editor still holding the old version must now be refused.
        update(teacher, id, optionsOnly).andExpect(status().isConflict());
    }

    @Test
    void updateRejectsOptionIdsFromAnotherQuestionAndDuplicates() throws Exception {
        User teacher = createUser("tina", Role.TEACHER);
        JsonNode mine = created(teacher, validBody("mine"));
        JsonNode other = created(teacher, validBody("other"));

        ObjectNode foreignOption = validBody("mine").put("version", mine.get("version").asLong());
        ((ObjectNode) foreignOption.get("options").get(0)).put("id", other.get("options").get(0).get("id").asLong());
        update(teacher, mine.get("id").asLong(), foreignOption).andExpect(status().isBadRequest());

        long sameId = mine.get("options").get(0).get("id").asLong();
        ObjectNode duplicated = validBody("mine").put("version", mine.get("version").asLong());
        ((ObjectNode) duplicated.get("options").get(0)).put("id", sameId);
        ((ObjectNode) duplicated.get("options").get(1)).put("id", sameId);
        update(teacher, mine.get("id").asLong(), duplicated).andExpect(status().isBadRequest());
    }

    @Test
    void teacherCannotModifyAnotherTeachersQuestionButCanReadIt() throws Exception {
        User tina = createUser("tina", Role.TEACHER);
        User theo = createUser("theo", Role.TEACHER);
        JsonNode tinasQuestion = created(tina, validBody("tina's question"));
        long id = tinasQuestion.get("id").asLong();

        as(theo, get("/api/teacher/questions/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(false)).andExpect(jsonPath("$.options[1].correct").value(true));
        update(theo, id, validBody("hijacked").put("version", tinasQuestion.get("version").asLong()))
                .andExpect(status().isForbidden());
        setActive(theo, id, false).andExpect(status().isForbidden());

        entityManager.flush();
        entityManager.clear();
        Question stored = questionRepository.findById(id).orElseThrow();
        assertThat(stored.getText()).isEqualTo("tina's question");
        assertThat(stored.isActive()).isTrue();
    }

    @Test
    void adminCanModifyAnyQuestionIncludingSeedQuestionsNobodyAuthored() throws Exception {
        User tina = createUser("tina", Role.TEACHER);
        User admin = createUser("root", Role.ADMIN);
        Question seedLike = createQuestion("jwt-security", Difficulty.EASY, "authored by SpringLab");
        JsonNode tinasQuestion = created(tina, validBody("tina's question"));

        update(admin, tinasQuestion.get("id").asLong(),
                validBody("admin edit").put("version", tinasQuestion.get("version").asLong()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.text").value("admin edit"));
        setActive(admin, seedLike.getId(), false).andExpect(status().isOk());

        // Seed questions have no author: teachers can read but not modify them.
        as(tina, get("/api/teacher/questions/" + seedLike.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.createdBy").value("SpringLab")).andExpect(jsonPath("$.editable").value(false));
        setActive(tina, seedLike.getId(), true).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- deactivation

    @Test
    void deactivatedQuestionsLeaveTheArenaButStayManageable() throws Exception {
        User teacher = createUser("tina", Role.TEACHER);
        User student = createUser("sam", Role.STUDENT);
        long id = created(teacher, validBody("to retire")).get("id").asLong();

        setActive(teacher, id, false).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        as(student, post("/api/challenges").contentType(MediaType.APPLICATION_JSON).content("{\"topic\":\"jwt-security\"}"))
                .andExpect(status().isConflict()); // nothing active left
        as(teacher, get("/api/teacher/questions/" + id)).andExpect(status().isOk());
        as(teacher, get("/api/teacher/questions?active=false")).andExpect(jsonPath("$.totalElements").value(1));

        setActive(teacher, id, true).andExpect(jsonPath("$.active").value(true));
        as(student, post("/api/challenges").contentType(MediaType.APPLICATION_JSON).content("{\"topic\":\"jwt-security\"}"))
                .andExpect(status().isCreated());
        setActive(teacher, id, false).andExpect(status().isOk());
        as(teacher, patch("/api/teacher/questions/" + id + "/active").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        as(teacher, get("/api/teacher/questions/999999999")).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- preview

    @Test
    void previewShowsExactlyWhatAStudentReceives() throws Exception {
        User teacher = createUser("tina", Role.TEACHER);
        long id = created(teacher, validBody("previewed")).get("id").asLong();

        as(teacher, get("/api/teacher/questions/" + id + "/preview")).andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("previewed"))
                .andExpect(jsonPath("$.options.length()").value(4))
                .andExpect(jsonPath("$.options[0].text").value("A"))
                .andExpect(content().string(not(containsString("correct"))))
                .andExpect(content().string(not(containsString("xplanation"))));
    }

    // ---------------------------------------------------------------- listing

    @Test
    void listsWithFiltersPaginationAndAnEditableFlag() throws Exception {
        User tina = createUser("tina", Role.TEACHER);
        User theo = createUser("theo", Role.TEACHER);
        created(tina, validBody("alpha"));
        created(tina, validBody("beta").put("difficulty", "HARD"));
        created(theo, validBody("gamma").put("topicId", topicId("jpa-hibernate")));
        Question retired = createQuestion("jwt-security", Difficulty.EASY, tina, "delta", 4, 1);
        retired.setActive(false);
        questionRepository.saveAndFlush(retired);

        as(tina, get("/api/teacher/questions?size=2&page=0&sort=id,asc")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));
        as(tina, get("/api/teacher/questions?mine=true")).andExpect(jsonPath("$.totalElements").value(3));
        as(tina, get("/api/teacher/questions?difficulty=HARD")).andExpect(jsonPath("$.totalElements").value(1));
        as(tina, get("/api/teacher/questions?topicId=" + topicId("jpa-hibernate")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].editable").value(false)); // theo's question
        as(tina, get("/api/teacher/questions?active=true&q=ALPH")).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].editable").value(true));
        as(tina, get("/api/teacher/questions?active=false")).andExpect(jsonPath("$.content[0].text").value("delta"));
        // Listing never reveals correctness or explanations.
        as(tina, get("/api/teacher/questions")).andExpect(content().string(not(containsString("explanation"))))
                .andExpect(content().string(not(containsString("\"correct\""))));
        // Sorting is restricted to a whitelist.
        as(tina, get("/api/teacher/questions?sort=explanation")).andExpect(status().isBadRequest());
        as(tina, get("/api/teacher/questions?difficulty=BOGUS")).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- history survives editing

    @Test
    void removingAnOptionStudentsAlreadyChoseKeepsTheirResultReadable() throws Exception {
        User teacher = createUser("tina", Role.TEACHER);
        User student = createUser("sam", Role.STUDENT);
        JsonNode question = created(teacher, validBody("history"));
        long questionId = question.get("id").asLong();
        long chosenOptionId = question.get("options").get(0).get("id").asLong(); // wrong answer, will be removed

        MvcResult started = as(student, post("/api/challenges").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn();
        long attemptId = objectMapper.readTree(started.getResponse().getContentAsString()).get("id").asLong();
        as(student, post("/api/challenges/" + attemptId + "/submit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":[{\"questionId\":" + questionId + ",\"optionId\":" + chosenOptionId + "}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(0));

        // In production the student's submission and the teacher's edit are different requests with different
        // persistence contexts. This test shares one transaction, so drop the student's loaded entities first.
        entityManager.flush();
        entityManager.clear();

        // The teacher removes the option the student picked (keeps the others, edited in place).
        ObjectNode body = objectMapper.createObjectNode();
        body.put("text", "history").put("explanation", "e").put("topicId", topicId("jwt-security"))
                .put("difficulty", "MEDIUM").put("version", question.get("version").asLong());
        ArrayNode options = body.putArray("options");
        options.addObject().put("id", question.get("options").get(1).get("id").asLong()).put("text", "B").put("correct", true);
        options.addObject().put("id", question.get("options").get(2).get("id").asLong()).put("text", "C").put("correct", false);
        update(teacher, questionId, body).andExpect(status().isOk()).andExpect(jsonPath("$.options.length()").value(2));

        entityManager.flush();
        entityManager.clear(); // force the result to be rebuilt from the database

        as(student, get("/api/challenges/" + attemptId + "/result")).andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(0))         // stored score is never recomputed
                .andExpect(jsonPath("$.questions[0].answered").value(false)) // the chosen option no longer exists
                .andExpect(jsonPath("$.questions[0].options.length()").value(2));
        Attempt stored = attemptRepository.findById(attemptId).orElseThrow();
        assertThat(stored.getScore()).isZero();
    }
}
