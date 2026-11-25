package com.csb.cinescope.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.csb.cinescope.FakeTmdbConfig;
import com.csb.cinescope.entity.Role;
import com.csb.cinescope.entity.User;
import com.csb.cinescope.repository.UserRepository;
import com.csb.cinescope.repository.WatchlistItemRepository;

/**
 * The way the first administrator is made: with {@code app.security.initial-admin} naming an existing account, that account
 * becomes an ADMIN on start-up. The setting is given with odd capitals and spaces on purpose. Own in-memory database, so the
 * other API tests cannot interfere.
 */
@SpringBootTest(properties = {
        "tmdb.read-token=" + FakeTmdbConfig.TOKEN,
        "tmdb.base-url=" + FakeTmdbConfig.BASE,
        "app.seed.demo-data=false",
        "app.security.initial-admin= Asha ",
        "spring.datasource.url=jdbc:h2:mem:cinescopeboot;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000" })
@ActiveProfiles("test")
@Import(FakeTmdbConfig.class)
class AdminBootstrapTest {

    @Autowired AdminBootstrap bootstrap;
    @Autowired UserRepository users;
    @Autowired WatchlistItemRepository items;
    @Autowired com.csb.cinescope.repository.CommentVoteRepository commentVotes;
    @Autowired com.csb.cinescope.repository.CommentRepository commentRepository;

    @BeforeEach
    void clean() {
        commentVotes.deleteAll();
        commentRepository.deleteAllInBatch();
        items.deleteAll();
        users.deleteAll();
    }

    @Test
    void theNamedAccountBecomesAnAdministratorAndItsOldSessionsEnd() {
        users.save(new User("asha", "asha@example.com", "hash", "Asha", "IN"));

        bootstrap.run(null);

        User asha = users.findByUsername("asha").orElseThrow();
        assertEquals(Role.ADMIN, asha.getRole());
        assertEquals(1, asha.getTokenVersion(), "a changed role ends the earlier sessions");
    }

    @Test
    void runningAgainChangesNothing() {
        users.save(new User("asha", "asha@example.com", "hash", "Asha", "IN"));
        bootstrap.run(null);
        int version = users.findByUsername("asha").orElseThrow().getTokenVersion();

        bootstrap.run(null);

        assertEquals(version, users.findByUsername("asha").orElseThrow().getTokenVersion());
        assertEquals(Role.ADMIN, users.findByUsername("asha").orElseThrow().getRole());
    }

    @Test
    void otherAccountsAreNeverTouchedAndAMissingAccountIsNotCreated() {
        users.save(new User("ravi", "ravi@example.com", "hash", "Ravi", "IN"));

        bootstrap.run(null);

        assertEquals(Role.USER, users.findByUsername("ravi").orElseThrow().getRole());
        assertTrue(users.findByUsername("asha").isEmpty(), "no account is invented for the name");
    }
}
