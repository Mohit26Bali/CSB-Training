package com.csb.cinescope.config;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.csb.cinescope.entity.Comment;
import com.csb.cinescope.entity.CommentStatus;
import com.csb.cinescope.entity.CommentVote;
import com.csb.cinescope.entity.Role;
import com.csb.cinescope.entity.User;
import com.csb.cinescope.entity.WatchStatus;
import com.csb.cinescope.entity.WatchlistItem;
import com.csb.cinescope.repository.CommentRepository;
import com.csb.cinescope.repository.CommentVoteRepository;
import com.csb.cinescope.repository.UserRepository;
import com.csb.cinescope.repository.WatchlistItemRepository;
import com.csb.cinescope.service.CommentProcessor;

/**
 * Creates a handful of demo accounts with ready-made watchlists, so a fresh database has something to sign in to and look at.
 * OFF by default: it runs only when {@code app.seed.demo-data=true} (environment variable {@code SEED_DEMO_DATA=true}), so a
 * real deployment never gets accounts with a published password.
 *
 * <p>Connections: writes through {@code repository/UserRepository} and {@code repository/WatchlistItemRepository}, hashing the
 * password with the {@link PasswordEncoder} bean from {@code config/SecurityConfig}, so the accounts are ordinary ones and sign
 * in through {@code AuthController} like anyone else. The watchlist rows carry the same snapshot (title, poster path, year) that
 * {@code service/WatchlistService} would fetch from TMDB when a title is added; here it is written down, so seeding needs no
 * network and no token. It is tested by {@code DemoDataInitializerTest} and used by the browser test e2e/demo-data.spec.ts.
 *
 * <p>It also writes about twenty comments, replies and "helpful" marks on well-known titles (the discussion section of the title page),
 * run through the real {@code service/CommentProcessor} so their sentiment, masking and processing steps are genuine. Two of them are
 * written to be HELD by the pipeline (strong language, link spam), so the administrator's moderation queue has something to review,
 * and one contains a mild bad word that comes out masked.
 *
 * <p>Safe to run on every start: an account that exists is left alone, and a list is filled only for an account that has none,
 * and comments are written only while there are none at all, so anything a person later changes or deletes is never put back.
 */
@Component
@ConditionalOnProperty(name = "app.seed.demo-data", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    /** The password of every demo account. It satisfies the sign-up password rules (security/PasswordPolicy). */
    public static final String DEMO_PASSWORD = "Cinema-Night-26!";

    /** The demo administrator has a password of its own, so sharing the ordinary one never hands out the admin screens. */
    public static final String ADMIN_PASSWORD = "Control-Room-26!";

    /** One demo account and the titles on its list. */
    public record DemoUser(String username, String displayName, String region, Role role, String password, List<Entry> list) {
    }

    /** One watchlist line: a title (by TMDB id and type) with its status, and a rating and note when watched. */
    public record Entry(String type, long tmdbId, WatchStatus status, Integer rating, String note) {
    }

    private record Title(String name, String posterPath, int year) {
    }

    /** What the list shows for each title, as TMDB reported it when this was written. Key: "type-id". */
    private static final Map<String, Title> TITLES = Map.ofEntries(
            Map.entry("movie-550", new Title("Fight Club", "/jSziioSwPVrOy9Yow3XhWIBDjq1.jpg", 1999)),
            Map.entry("movie-27205", new Title("Inception", "/xlaY2zyzMfkhk0HSC5VUwzoZPU1.jpg", 2010)),
            Map.entry("movie-157336", new Title("Interstellar", "/yQvGrMoipbRoddT0ZR8tPoR7NfX.jpg", 2014)),
            Map.entry("movie-155", new Title("The Dark Knight", "/qJ2tW6WMUDux911r6m7haRef0WH.jpg", 2008)),
            Map.entry("movie-496243", new Title("Parasite", "/7IiTTgloJzvGI1TAYymCfbfl3vT.jpg", 2019)),
            Map.entry("movie-20453", new Title("3 Idiots", "/66A9MqXOyVFCssoloscw79z8Tew.jpg", 2009)),
            Map.entry("movie-360814", new Title("Dangal", "/cJRPOLEexI7qp2DKtFfCh7YaaUG.jpg", 2016)),
            Map.entry("movie-129", new Title("Spirited Away", "/39wmItIWsg5sZMyRUHLkWBcuVCM.jpg", 2001)),
            Map.entry("movie-37165", new Title("The Truman Show", "/vuza0WqY239yBXOadKlGwJsZJFE.jpg", 1998)),
            Map.entry("movie-489", new Title("Good Will Hunting", "/z2FnLKpFi1HPO7BEJxdkv6hpJSU.jpg", 1997)),
            Map.entry("movie-1402", new Title("The Pursuit of Happyness", "/lBYOKAMcxIvuk9s9hMuecB9dPBV.jpg", 2006)),
            Map.entry("movie-13", new Title("Forrest Gump", "/saHP97rTPS5eLmrLQEcANmKrsFl.jpg", 1994)),
            Map.entry("movie-278", new Title("The Shawshank Redemption", "/9cqNxx0GxF0bflZmeSMuL5tnGzr.jpg", 1994)),
            Map.entry("movie-238", new Title("The Godfather", "/3bhkrj58Vtu7enYsRolD1fZdja1.jpg", 1972)),
            Map.entry("movie-8587", new Title("The Lion King", "/sKCr78MXSLixwmZ8DyJLrpMsd15.jpg", 1994)),
            Map.entry("movie-14163", new Title("Chak De! India", "/mmFMgEsTRCAGAtwffGpuo3mJsxN.jpg", 2007)),
            Map.entry("movie-603", new Title("The Matrix", "/dXNAPwY7VrqMAo51EKhhCJfaGb5.jpg", 1999)),
            Map.entry("movie-680", new Title("Pulp Fiction", "/vQWk5YBFWF4bZaofAbv0tShwBvQ.jpg", 1994)),
            Map.entry("tv-1396", new Title("Breaking Bad", "/anFx9aTOOYqgS3v7x3R84Kz67ly.jpg", 2008)),
            Map.entry("tv-66732", new Title("Stranger Things", "/uOOtwVbSr4QDjAGIifLDwpb2Pdl.jpg", 2016)),
            Map.entry("tv-1399", new Title("Game of Thrones", "/1XS1oqL89opfnbLl8WnZY1O1uJx.jpg", 2011)),
            Map.entry("tv-2316", new Title("The Office", "/7DJKHzAi83BmQrWLrYYOqcoKfhR.jpg", 2005)),
            Map.entry("tv-71446", new Title("Money Heist", "/reEMJA1uzscCbkpeRJeTT2bjqUp.jpg", 2017)),
            Map.entry("tv-60625", new Title("Rick and Morty", "/owhkU6KRqdXoUQpjV8uyZGPtX58.jpg", 2013)),
            Map.entry("tv-1418", new Title("The Big Bang Theory", "/euKFiO5M125rpngFRBbSW83beeI.jpg", 2007)));

    /**
     * The demo accounts: asha has a big, varied list; ravi leans on classics; meera watches series; sam lives in the US; karan
     * is brand new with an empty list (to see the empty states); admin is the one administrator (its own password, see
 * ADMIN_PASSWORD), who can open the admin screens.
     */
    public static final List<DemoUser> USERS = List.of(
            new DemoUser("asha", "Asha Rao", "IN", Role.USER, DEMO_PASSWORD, List.of(
                    new Entry("movie", 27205, WatchStatus.WATCHED, 9, "Rewatched it twice. The ending still bugs me."),
                    new Entry("movie", 157336, WatchStatus.WATCHED, 10, "Cried at the docking scene."),
                    new Entry("movie", 155, WatchStatus.WATCHED, 9, null),
                    new Entry("movie", 20453, WatchStatus.WATCHED, 10, "All is well."),
                    new Entry("movie", 360814, WatchStatus.WATCHING, null, "Halfway through, brilliant."),
                    new Entry("tv", 1396, WatchStatus.WATCHING, null, "Season 3."),
                    new Entry("movie", 496243, WatchStatus.WANT, null, "Everyone keeps recommending it."),
                    new Entry("movie", 129, WatchStatus.WANT, null, null),
                    new Entry("tv", 71446, WatchStatus.WANT, null, null))),
            new DemoUser("ravi", "Ravi Kumar", "IN", Role.USER, DEMO_PASSWORD, List.of(
                    new Entry("movie", 550, WatchStatus.WATCHED, 9, null),
                    new Entry("movie", 238, WatchStatus.WATCHED, 10, "Slow burn, perfect."),
                    new Entry("movie", 680, WatchStatus.WATCHED, 8, null),
                    new Entry("tv", 1399, WatchStatus.WATCHING, null, "Season 5 and I'm not okay."),
                    new Entry("movie", 603, WatchStatus.WANT, null, null),
                    new Entry("movie", 14163, WatchStatus.WANT, null, "Sattar minute!"))),
            new DemoUser("meera", "Meera Iyer", "IN", Role.USER, DEMO_PASSWORD, List.of(
                    new Entry("tv", 66732, WatchStatus.WATCHED, 8, null),
                    new Entry("tv", 2316, WatchStatus.WATCHED, 9, "Comfort rewatch."),
                    new Entry("tv", 60625, WatchStatus.WATCHING, null, null),
                    new Entry("tv", 1418, WatchStatus.WANT, null, null),
                    new Entry("movie", 13, WatchStatus.WANT, null, null))),
            new DemoUser("sam", "Sam Taylor", "US", Role.USER, DEMO_PASSWORD, List.of(
                    new Entry("movie", 489, WatchStatus.WATCHED, 9, "It's not your fault."),
                    new Entry("movie", 278, WatchStatus.WATCHED, 10, null),
                    new Entry("movie", 8587, WatchStatus.WATCHED, 8, null),
                    new Entry("movie", 37165, WatchStatus.WANT, null, null),
                    new Entry("movie", 1402, WatchStatus.WANT, null, "Heard it is a tearjerker."))),
            new DemoUser("karan", "Karan Mehta", "IN", Role.USER, DEMO_PASSWORD, List.of()),
            new DemoUser("admin", "Site Admin", "IN", Role.ADMIN, ADMIN_PASSWORD, List.of()));

    /** A reply written by a demo account; {@code helpfulBy} are the usernames that marked it helpful. */
    public record SeedReply(String author, String body, int hoursAgo, List<String> helpfulBy) {
    }

    /** A comment (with an optional rating) written by a demo account, with its replies. */
    public record SeedComment(String author, String type, long tmdbId, Integer rating, String body, int hoursAgo, List<String> helpfulBy, List<SeedReply> replies) {
    }

    /**
     * The demo discussion. The texts are ordinary opinions, chosen to show every part of the pipeline: strong praise and
     * strong criticism (sentiment), a negation ("not for me"), one mild bad word (masked), and two comments that get held.
     */
    public static final List<SeedComment> COMMENTS = List.of(
            new SeedComment("asha", "movie", 27205, 9, "Rewatched it twice and the ending still bugs me. Absolutely brilliant, the layered dream idea is executed so well.", 70,
                    List.of("ravi", "meera", "sam"), List.of(
                            new SeedReply("ravi", "Same here! That spinning top shot is the best ending ever.", 65, List.of("asha")))),
            new SeedComment("meera", "movie", 27205, 7, "Loved the visuals and the music, but the exposition is a bit heavy. Still a very good film.", 50, List.of("asha"), List.of()),
            new SeedComment("sam", "movie", 27205, 5, "Honestly a bit overrated. I found the plot confusing and the middle felt slow.", 30, List.of("ravi"), List.of(
                    new SeedReply("asha", "Give it a second watch, it clicks once you know the rules of the dreams.", 28, List.of("sam", "meera")))),
            new SeedComment("asha", "movie", 157336, 10, "Cried at the docking scene. A powerful, beautiful story about love and time, and the score is perfect.", 120,
                    List.of("ravi", "meera", "sam"), List.of(
                            new SeedReply("meera", "The organ music gives me goosebumps every time.", 100, List.of("asha")))),
            new SeedComment("ravi", "movie", 157336, 8, "Stunning visuals and a great score, though some of the science talk loses me.", 90, List.of("asha"), List.of()),
            new SeedComment("ravi", "movie", 155, 10, "Heath Ledger is unforgettable. The best superhero film, flawless from start to finish.", 200, List.of("asha", "sam", "meera"), List.of()),
            new SeedComment("asha", "movie", 155, 9, "Dark, gripping and surprisingly deep. The interrogation scene is incredible.", 150, List.of("ravi"), List.of()),
            new SeedComment("asha", "movie", 20453, 10, "All is well! Funny, touching and honestly life changing. I recommend it to every student.", 300, List.of("ravi", "meera", "sam"), List.of(
                    new SeedReply("sam", "Watched it with my whole family and everyone loved it.", 290, List.of("asha")))),
            new SeedComment("sam", "movie", 20453, 9, "Great message about following your passion, and the humour is wonderful.", 260, List.of("meera"), List.of()),
            new SeedComment("ravi", "movie", 20453, 6, "Good but a little predictable, and the second half is slow.", 230, List.of(), List.of()),
            new SeedComment("ravi", "movie", 550, 9, "A dark, clever film and the twist is perfect. Not for everyone.", 400, List.of("sam"), List.of()),
            new SeedComment("sam", "movie", 550, 4, "Boring middle and I did not enjoy the violence. Not for me.", 380, List.of(), List.of()),
            new SeedComment("karan", "movie", 550, null, "Absolute shit ending, what a waste of two hours.", 2, List.of(), List.of()),
            new SeedComment("karan", "movie", 550, null, "Watch it free here http://a.example http://b.example http://c.example", 1, List.of(), List.of()),
            new SeedComment("meera", "movie", 496243, 8, "Brilliant and tense. The tonal shifts are amazing, and the cast is perfect.", 180, List.of("asha", "ravi"), List.of()),
            new SeedComment("karan", "movie", 496243, 3, "Parasite was stupid and overrated, honestly a waste. The first hour is boring.", 160, List.of(), List.of()),
            new SeedComment("ravi", "tv", 1396, 10, "Perfect writing. The best show ever made, every season gets better.", 500, List.of("asha", "meera"), List.of(
                    new SeedReply("meera", "Not a fan of the slow start, but season four is superb.", 480, List.of("ravi")))));

    private final UserRepository users;
    private final WatchlistItemRepository watchlist;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaction;
    private final CommentProcessor processor;
    private final CommentRepository comments;
    private final CommentVoteRepository commentVotes;

    public DemoDataInitializer(UserRepository users, WatchlistItemRepository watchlist, PasswordEncoder passwordEncoder,
                               TransactionTemplate transaction, CommentProcessor processor, CommentRepository comments,
                               CommentVoteRepository commentVotes) {
        this.users = users;
        this.watchlist = watchlist;
        this.passwordEncoder = passwordEncoder;
        this.transaction = transaction;
        this.processor = processor;
        this.comments = comments;
        this.commentVotes = commentVotes;
    }

    @Override
    public void run(ApplicationArguments args) {
        int[] created = { 0, 0, 0 };
        transaction.executeWithoutResult(status -> {
            for (DemoUser demo : USERS) {
                User user = users.findByUsername(demo.username()).orElse(null);
                if (user == null) {
                    user = users.save(new User(demo.username(), demo.username() + "@example.com", passwordEncoder.encode(demo.password()),
                            demo.displayName(), demo.region(), demo.role()));
                    created[0]++;
                }
                if (watchlist.countByStatus(user).isEmpty()) {
                    for (Entry entry : demo.list()) {
                        Title title = TITLES.get(entry.type() + "-" + entry.tmdbId());
                        WatchlistItem item = new WatchlistItem(user, entry.tmdbId(), entry.type(), title.name(), title.posterPath(), title.year());
                        item.update(entry.status(), entry.rating(), entry.note());
                        watchlist.save(item);
                        created[1]++;
                    }
                }
            }
            if (comments.count() == 0) {
                created[2] = seedComments();
            }
        });
        log.warn("Demo data is ON (app.seed.demo-data=true): {} new accounts, {} watchlist entries and {} comments. "
                + "Demo password \"{}\" (administrator: \"{}\") - never enable this on a real deployment.", created[0], created[1], created[2], DEMO_PASSWORD, ADMIN_PASSWORD);
    }

    /** Writes the demo discussion; returns how many comments and replies were created. */
    private int seedComments() {
        Map<String, User> byName = new HashMap<>();
        for (DemoUser demo : USERS) {
            users.findByUsername(demo.username()).ifPresent(u -> byName.put(demo.username(), u));
        }
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (SeedComment seed : COMMENTS) {
            Comment comment = write(byName.get(seed.author()), seed.type(), seed.tmdbId(), null, seed.rating(), seed.body(), now.minusHours(seed.hoursAgo()), seed.helpfulBy(), byName);
            count++;
            for (SeedReply reply : seed.replies()) {
                write(byName.get(reply.author()), seed.type(), seed.tmdbId(), comment, null, reply.body(), now.minusHours(reply.hoursAgo()), reply.helpfulBy(), byName);
                count++;
            }
        }
        return count;
    }

    private Comment write(User author, String type, long tmdbId, Comment parent, Integer rating, String body, LocalDateTime when, List<String> helpfulBy,
                          Map<String, User> byName) {
        CommentProcessor.Result result = processor.process(body);
        Comment comment = new Comment(author, tmdbId, type, parent, result.text(), rating, result.sentiment(), result.sentimentScore(), result.status(), result.encodedSteps());
        comment.backdate(when);
        comments.save(comment);
        if (result.status() == CommentStatus.PUBLISHED) {
            for (String username : helpfulBy) {
                commentVotes.save(new CommentVote(comment, byName.get(username)));
            }
            comment.setHelpfulCount(helpfulBy.size());
            comments.save(comment);
        }
        return comment;
    }

    /** The snapshot a list shows for a title; null for a title this class does not know (used by the tests). */
    static String nameOf(String type, long tmdbId) {
        Title title = TITLES.get(type + "-" + tmdbId);
        return title == null ? null : title.name();
    }
}
