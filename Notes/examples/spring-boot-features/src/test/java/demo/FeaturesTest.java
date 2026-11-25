package demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import demo.aop.AuditAspect;
import demo.aop.TimingAspect;
import demo.cache.PriceService;
import demo.config.MailProperties;
import demo.service.OrderService;
import demo.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FeaturesTest {

    @Autowired MailProperties mail;
    @Autowired OrderService orders;
    @Autowired PriceService prices;
    @Autowired ReportService reports;
    @Autowired MockMvc mvc;

    @BeforeEach
    void reset() {
        TimingAspect.TIMED.clear();
        AuditAspect.EVENTS.clear();
        prices.clearAll();
        prices.resetLookups();
    }

    // ---------- configuration ----------
    @Test
    void configurationIsBoundIntoARecord() {
        assertThat(mail.host()).isEqualTo("smtp.example.com");
        assertThat(mail.port()).isEqualTo(587);
        assertThat(mail.retries()).isEqualTo(3);                 // dev profile is the default: prod values are not used
    }

    // ---------- AOP ----------
    @Test
    void beansWithAspectsAreProxies() {
        assertThat(AopUtils.isAopProxy(orders)).isTrue();
        assertThat(AopUtils.isCglibProxy(orders)).isTrue();      // a class without an interface gets a CGLIB subclass proxy
    }

    @Test
    void aroundAdviceTimesAnnotatedMethods() {
        assertThat(orders.place("book")).isEqualTo("ordered book");
        assertThat(TimingAspect.TIMED).containsExactly("OrderService.place(..)");
    }

    @Test
    void selfInvocationBypassesTheProxy() {
        orders.placeTwice("pen");                                // place() is called twice from inside the object
        assertThat(TimingAspect.TIMED).isEmpty();                // so @Timed never fired
        assertThat(AuditAspect.EVENTS).containsExactly("before placeTwice", "returned ordered pen, ordered pen");
    }

    @Test
    void beforeReturningAndThrowingAdvice() {
        orders.place("a");
        assertThatThrownBy(() -> orders.place(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(AuditAspect.EVENTS).containsExactly("before place", "returned ordered a", "before place", "failed empty item");
    }

    // ---------- caching ----------
    @Test
    void cacheableRunsTheMethodOnlyOnce() {
        assertThat(prices.priceOf("shoe")).isEqualTo(400);
        assertThat(prices.priceOf("shoe")).isEqualTo(400);
        assertThat(prices.priceOf("shoe")).isEqualTo(400);
        assertThat(prices.lookups()).isEqualTo(1);
    }

    @Test
    void cachePutRefreshesAndCacheEvictForgets() {
        prices.priceOf("hat");                                   // 300, cached
        prices.updatePrice("hat", 999);                          // @CachePut replaces the cached value
        assertThat(prices.priceOf("hat")).isEqualTo(999);
        assertThat(prices.lookups()).isEqualTo(1);           // the method body never ran again
        prices.discontinue("hat");                               // @CacheEvict
        assertThat(prices.priceOf("hat")).isEqualTo(300);        // recomputed
        assertThat(prices.lookups()).isEqualTo(2);
    }

    @Test
    void selfInvocationBypassesTheCacheToo() {
        prices.cachedViaSelfCall("bag");
        prices.cachedViaSelfCall("bag");
        assertThat(prices.lookups()).isEqualTo(2);           // nothing was cached
    }

    // ---------- async ----------
    @Test
    void asyncRunsOnAnotherThread() throws Exception {
        String result = reports.buildReport("sales").get();
        assertThat(result).startsWith("sales on ").doesNotContain("main");
    }

    // ---------- web ----------
    @Test
    void controllerReadsConfiguration() throws Exception {
        mvc.perform(get("/mail-host")).andExpect(status().isOk()).andExpect(content().string("smtp.example.com:587"));
    }

    @Test
    void uploadAcceptsAllowedTypesUnderARandomName() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "../../evil name.PNG", "image/png", new byte[] {1, 2, 3});
        String body = mvc.perform(multipart("/files").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.endsWith(".png")))
                .andReturn().getResponse().getContentAsString();
        String id = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        assertThat(id).doesNotContain("evil");
        mvc.perform(get("/files/" + id)).andExpect(status().isOk()).andExpect(header().exists("Content-Disposition"));
    }

    @Test
    void uploadRefusesOtherTypesAndEmptyFiles() throws Exception {
        mvc.perform(multipart("/files").file(new MockMultipartFile("file", "run.exe", "application/octet-stream", new byte[] {1})))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart("/files").file(new MockMultipartFile("file", "a.png", "image/png", new byte[0])))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadCannotEscapeTheUploadFolder() throws Exception {
        mvc.perform(get("/files/..%2F..%2Fetc%2Fpasswd")).andExpect(status().isNotFound());
    }

    // ---------- actuator ----------
    @Test
    void healthEndpointIsUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void endpointsThatAreNotExposedAreNotFound() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    }
}
