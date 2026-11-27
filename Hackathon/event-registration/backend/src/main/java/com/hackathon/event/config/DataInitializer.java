package com.hackathon.event.config;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.event.entity.Event;
import com.hackathon.event.entity.EventStatus;
import com.hackathon.event.entity.Registration;
import com.hackathon.event.entity.RegistrationStatus;
import com.hackathon.event.entity.Role;
import com.hackathon.event.entity.User;
import com.hackathon.event.repo.EventRepository;
import com.hackathon.event.repo.RegistrationRepository;
import com.hackathon.event.repo.UserRepository;

/**
 * Seeds demo users, events (past, upcoming, one almost full, one full with a waitlist, a draft and a cancelled one)
 * and registrations. Idempotent: users are only created when missing, events and registrations only when there are none.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private static final List<String[]> ADMINS = List.of(
            new String[] { "eventadmin", "Priya Nambiar" },
            new String[] { "rahul.deshpande", "Rahul Deshpande" });

    private static final List<String[]> EMPLOYEES = List.of(
            new String[] { "aarav.sharma", "Aarav Sharma" }, new String[] { "diya.patel", "Diya Patel" },
            new String[] { "rohan.mehta", "Rohan Mehta" }, new String[] { "ananya.iyer", "Ananya Iyer" },
            new String[] { "vivaan.gupta", "Vivaan Gupta" }, new String[] { "kavya.nair", "Kavya Nair" },
            new String[] { "arjun.reddy", "Arjun Reddy" }, new String[] { "meera.joshi", "Meera Joshi" },
            new String[] { "ishaan.verma", "Ishaan Verma" }, new String[] { "saanvi.rao", "Saanvi Rao" },
            new String[] { "aditya.singh", "Aditya Singh" }, new String[] { "karan.malhotra", "Karan Malhotra" },
            new String[] { "neha.kapoor", "Neha Kapoor" }, new String[] { "siddharth.jain", "Siddharth Jain" },
            new String[] { "pooja.bhatt", "Pooja Bhatt" }, new String[] { "manish.kulkarni", "Manish Kulkarni" },
            new String[] { "tanvi.desai", "Tanvi Desai" }, new String[] { "harsh.vora", "Harsh Vora" },
            new String[] { "ritika.sen", "Ritika Sen" }, new String[] { "varun.chopra", "Varun Chopra" },
            new String[] { "shreya.menon", "Shreya Menon" }, new String[] { "nikhil.pillai", "Nikhil Pillai" },
            new String[] { "aditi.rao", "Aditi Rao" }, new String[] { "gaurav.bhatia", "Gaurav Bhatia" },
            new String[] { "swati.mishra", "Swati Mishra" }, new String[] { "rajesh.nambiar", "Rajesh Nambiar" });

    /** title, category, venue, description, day offset, start hour, hours long, capacity, status, confirmed, waitlisted */
    private static final List<Object[]> EVENTS = List.of(
            new Object[] { "Annual Town Hall 2026", "Town Hall", "Main Auditorium, Block A",
                    "Leadership shares the year so far, answers your questions and announces what is next for every team.",
                    -12, 11, 2, 120, EventStatus.PUBLISHED, 22, 0 },
            new Object[] { "Intro to Cloud Security", "Tech Talk", "Seminar Room 2",
                    "A practical walk through IAM, secrets and the most common cloud misconfigurations. Recording available to attendees.",
                    -5, 15, 2, 40, EventStatus.PUBLISHED, 26, 0 },
            new Object[] { "Spring Boot Performance Clinic", "Workshop", "Lab 3",
                    "Bring a slow endpoint and leave with a fix. Profiling, connection pools and caching, hands-on in small groups.",
                    3, 10, 4, 25, EventStatus.PUBLISHED, 24, 0 },
            new Object[] { "Inter-Office Cricket Cup", "Sports", "City Sports Ground",
                    "Eight teams, one trophy. Register as a player; team captains will pick squads the week before.",
                    6, 9, 7, 20, EventStatus.PUBLISHED, 20, 5 },
            new Object[] { "Diwali Rangoli and Lights", "Cultural", "Atrium, Ground Floor",
                    "Design a rangoli with your team, enjoy sweets and vote for the best one. Families welcome.",
                    9, 16, 3, 80, EventStatus.PUBLISHED, 25, 0 },
            new Object[] { "Mindfulness and Stress Reset", "Wellness", "Wellness Room",
                    "A guided 90-minute session on breathing, posture and short breaks that actually work at a desk.",
                    4, 13, 2, 20, EventStatus.PUBLISHED, 18, 0 },
            new Object[] { "Design Systems Meetup", "Tech Talk", "Seminar Room 1",
                    "Three short talks on building and governing a shared component library, followed by open discussion.",
                    12, 17, 2, 50, EventStatus.PUBLISHED, 22, 0 },
            new Object[] { "Data Storytelling Workshop", "Workshop", "Lab 1",
                    "Turn a spreadsheet into a story people remember. Bring a dataset you work with.",
                    15, 10, 3, 30, EventStatus.PUBLISHED, 12, 0 },
            new Object[] { "Friday Board Games Evening", "Social", "Cafeteria, Level 2",
                    "Catan, Codenames and snacks. Come alone or bring your team; we will find you a table.",
                    8, 18, 3, 40, EventStatus.PUBLISHED, 24, 0 },
            new Object[] { "Morning Yoga by the Lake", "Wellness", "Lakeside Lawn",
                    "A gentle 60-minute class for all levels. Mats provided.",
                    2, 7, 1, 15, EventStatus.PUBLISHED, 15, 3 },
            new Object[] { "Open Source Contribution Day", "Workshop", "Lab 2",
                    "Pick an open-source issue, pair with a mentor and open your first pull request before lunch.",
                    21, 10, 6, 35, EventStatus.PUBLISHED, 9, 0 },
            new Object[] { "Leadership AMA with the CTO", "Town Hall", "Main Auditorium, Block A",
                    "Submit questions in advance or ask live. Topics so far: roadmap, hiring and remote work.",
                    18, 15, 1, 150, EventStatus.PUBLISHED, 26, 0 },
            new Object[] { "Hackathon Kick-off", "Tech Talk", "Innovation Lab",
                    "Rules, themes and team formation for next month's hackathon. Details to be confirmed.",
                    30, 10, 2, 100, EventStatus.DRAFT, 0, 0 },
            new Object[] { "Rooftop Music Night", "Cultural", "Rooftop Terrace",
                    "Cancelled because of the weather forecast; we will announce a new date.",
                    10, 19, 3, 70, EventStatus.CANCELLED, 0, 0 });

    private final UserRepository users;
    private final EventRepository events;
    private final RegistrationRepository registrations;
    private final PasswordEncoder encoder;

    public DataInitializer(UserRepository users, EventRepository events, RegistrationRepository registrations,
                           PasswordEncoder encoder) {
        this.users = users;
        this.events = events;
        this.registrations = registrations;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers(ADMINS, Role.EVENT_ADMIN, "admin123");
        seedUsers(EMPLOYEES, Role.EMPLOYEE, "employee123");
        if (events.count() == 0) {
            seedEvents();
        }
    }

    private void seedUsers(List<String[]> people, Role role, String password) {
        String hash = encoder.encode(password);
        users.saveAll(people.stream().filter(p -> !users.existsByUsername(p[0]))
                .map(p -> new User(p[0], hash, p[1], role)).toList());
    }

    private void seedEvents() {
        Random random = new Random(18);
        LocalDateTime now = LocalDateTime.now();
        List<User> employees = new ArrayList<>(EMPLOYEES.stream().map(e -> users.findByUsername(e[0]).orElseThrow()).toList());
        List<Registration> toSave = new ArrayList<>();

        // the demo employee stays free for these events, so Register can be tried on them
        Set<Integer> freeForDemo = Set.of(2, 5, 7, 8, 10);
        for (int index = 0; index < EVENTS.size(); index++) {
            Object[] row = EVENTS.get(index);
            final int idx = index;
            LocalDateTime start = now.toLocalDate().plusDays((int) row[4]).atTime(LocalTime.of((int) row[5], 0));
            Event event = new Event((String) row[0], (String) row[1], (String) row[2], (String) row[3], start,
                    start.plusHours((int) row[6]), (int) row[7], ADMINS.get(random.nextInt(ADMINS.size()))[0]);
            EventStatus status = (EventStatus) row[8];
            if (status != EventStatus.DRAFT) {
                event.publish();
            }
            if (status == EventStatus.CANCELLED) {
                event.cancel();
            }
            event.setCreatedAt(start.minusDays(14 + random.nextInt(10)));
            events.save(event);

            int confirmed = (int) row[9];
            int waitlisted = (int) row[10];
            Collections.shuffle(employees, random);
            List<User> pool = employees.stream()
                    .filter(u -> !(freeForDemo.contains(idx) && u.getUsername().equals("aarav.sharma"))).toList();
            int people = Math.min(pool.size(), confirmed + waitlisted);
            int confirmedPeople = Math.min(confirmed, people - waitlisted);
            for (int i = 0; i < people; i++) {
                boolean isConfirmed = i < confirmedPeople;
                Registration r = new Registration(event, pool.get(i),
                        isConfirmed ? RegistrationStatus.CONFIRMED : RegistrationStatus.WAITLISTED);
                r.setRegisteredAt(start.minusDays(12).plusHours(i * 7L + random.nextInt(5)));
                toSave.add(r);
            }
        }
        registrations.saveAll(toSave);
    }
}
