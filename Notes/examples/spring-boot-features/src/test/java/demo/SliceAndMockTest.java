package demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import demo.config.MailProperties;
import demo.service.OrderService;
import demo.web.HelloController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** A "slice" test: only the web layer is started, collaborators are replaced by Mockito mocks. */
@WebMvcTest(HelloController.class)
class SliceAndMockTest {

    @Autowired MockMvc mvc;
    @MockitoBean OrderService orders;                            // Boot 3 called this @MockBean
    @MockitoBean MailProperties mail;

    @Test
    void controllerDelegatesToTheService() throws Exception {
        when(orders.place("lamp")).thenReturn("ordered lamp (mock)");
        mvc.perform(get("/order").param("item", "lamp")).andExpect(status().isOk()).andExpect(content().string("ordered lamp (mock)"));
        verify(orders).place("lamp");
    }

    @Test
    void missingParameterIsABadRequestAndTheServiceIsNeverCalled() throws Exception {
        mvc.perform(get("/order")).andExpect(status().isBadRequest());
        verify(orders, never()).place(anyString());
    }

    @Test
    void mocksReturnDefaultsUntilStubbed() {
        assertThat(orders.place("x")).isNull();                  // an unstubbed mock returns null
    }
}
