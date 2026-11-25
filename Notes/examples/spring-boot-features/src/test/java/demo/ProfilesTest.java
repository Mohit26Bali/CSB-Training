package demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import demo.config.MailProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.Environment;

class ProfilesTest {

    @Test
    void prodProfileOverridesOnlyWhatItDeclares() {
        try (var ctx = new SpringApplicationBuilder(App.class).profiles("prod").web(org.springframework.boot.WebApplicationType.NONE).run()) {
            MailProperties mail = ctx.getBean(MailProperties.class);
            assertThat(mail.host()).isEqualTo("smtp.prod.example.com");    // overridden by the prod document
            assertThat(mail.retries()).isEqualTo(5);                         // overridden
            assertThat(mail.port()).isEqualTo(587);                          // not mentioned in prod: inherited
            assertThat(ctx.getEnvironment().getActiveProfiles()).containsExactly("prod");
        }
    }

    @Test
    void defaultProfileIsDevWhenNothingIsSet() {
        try (var ctx = new SpringApplicationBuilder(App.class).web(org.springframework.boot.WebApplicationType.NONE).run()) {
            Environment env = ctx.getEnvironment();
            assertThat(env.getActiveProfiles()).isEmpty();
            assertThat(env.getDefaultProfiles()).containsExactly("dev");
        }
    }

    @Test
    void commandLineAndEnvironmentStyleOverridesWin() {
        try (var ctx = new SpringApplicationBuilder(App.class).web(org.springframework.boot.WebApplicationType.NONE)
                .run("--app.mail.port=2525")) {
            assertThat(ctx.getBean(MailProperties.class).port()).isEqualTo(2525);   // command line beats application.yml
        }
    }

    @Test
    void invalidConfigurationStopsTheApplicationAtStartUp() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(App.class).web(org.springframework.boot.WebApplicationType.NONE)
                .run("--app.mail.port=70000", "--app.mail.sender=not-an-email"))
                .hasRootCauseInstanceOf(org.springframework.boot.context.properties.bind.validation.BindValidationException.class);
    }
}
