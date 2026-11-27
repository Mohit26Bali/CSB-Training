package com.hackathon.enrollment.config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.enrollment.entity.Course;
import com.hackathon.enrollment.entity.Enrollment;
import com.hackathon.enrollment.entity.EnrollmentStatus;
import com.hackathon.enrollment.entity.Role;
import com.hackathon.enrollment.entity.User;
import com.hackathon.enrollment.repo.CourseRepository;
import com.hackathon.enrollment.repo.EnrollmentRepository;
import com.hackathon.enrollment.repo.UserRepository;

/**
 * Seeds demo data: the admin, 30 employees, a catalogue of courses with pictures and some enrollments.
 * Idempotent: anything that already exists (matched by username, course title, or user+course) is left alone.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private static final String EMPLOYEE_PASSWORD = "employee123";

    /** username, full name */
    private static final List<String[]> EMPLOYEES = List.of(
            new String[] { "employee1", "Employee One" },
            new String[] { "employee2", "Employee Two" },
            new String[] { "employee3", "Employee Three" },
            new String[] { "employee4", "Employee Four" },
            new String[] { "aarav.sharma", "Aarav Sharma" },
            new String[] { "diya.patel", "Diya Patel" },
            new String[] { "rohan.mehta", "Rohan Mehta" },
            new String[] { "ananya.iyer", "Ananya Iyer" },
            new String[] { "vivaan.gupta", "Vivaan Gupta" },
            new String[] { "kavya.nair", "Kavya Nair" },
            new String[] { "arjun.reddy", "Arjun Reddy" },
            new String[] { "meera.joshi", "Meera Joshi" },
            new String[] { "ishaan.verma", "Ishaan Verma" },
            new String[] { "saanvi.rao", "Saanvi Rao" },
            new String[] { "aditya.singh", "Aditya Singh" },
            new String[] { "neha.kapoor", "Neha Kapoor" },
            new String[] { "karan.malhotra", "Karan Malhotra" },
            new String[] { "pooja.desai", "Pooja Desai" },
            new String[] { "rahul.bose", "Rahul Bose" },
            new String[] { "sneha.kulkarni", "Sneha Kulkarni" },
            new String[] { "vikram.chauhan", "Vikram Chauhan" },
            new String[] { "priya.menon", "Priya Menon" },
            new String[] { "siddharth.jain", "Siddharth Jain" },
            new String[] { "tanvi.bhatt", "Tanvi Bhatt" },
            new String[] { "manish.agarwal", "Manish Agarwal" },
            new String[] { "riya.banerjee", "Riya Banerjee" },
            new String[] { "harsh.vardhan", "Harsh Vardhan" },
            new String[] { "nisha.pandey", "Nisha Pandey" },
            new String[] { "devansh.trivedi", "Devansh Trivedi" },
            new String[] { "shreya.ghosh", "Shreya Ghosh" });

    /** title, category, capacity, description */
    private static final List<Object[]> COURSES = List.of(
            new Object[] { "Java Basics", "Programming", 2, "Intro to Java" },
            new Object[] { "Spring Boot", "Programming", 3, "REST APIs with Spring" },
            new Object[] { "SQL Fundamentals", "Database", 3, "Queries, joins and indexes" },
            new Object[] { "Cyber Awareness", "Compliance", 1, "Company security training" },
            new Object[] { "Python for Data Analysis", "Data", 20,
                    "Clean, explore and visualise data with pandas and matplotlib." },
            new Object[] { "React Fundamentals", "Programming", 15,
                    "Components, state and hooks: build a small interactive web app." },
            new Object[] { "Cloud Computing with AWS", "Cloud", 12,
                    "Core AWS services, IAM basics and deploying your first application." },
            new Object[] { "Kubernetes in Practice", "DevOps", 10,
                    "Run, scale and debug containerised workloads on a cluster." },
            new Object[] { "Agile & Scrum Essentials", "Management", 25,
                    "Sprints, ceremonies and backlog habits that keep teams shipping." },
            new Object[] { "Effective Communication", "Soft Skills", 30,
                    "Clear writing, tough conversations and presenting to stakeholders." },
            new Object[] { "Leadership for New Managers", "Management", 4,
                    "Moving from individual contributor to leading a team." },
            new Object[] { "Data Privacy & GDPR", "Compliance", 40,
                    "What personal data is, how to handle it, and when to escalate." },
            new Object[] { "Git & Version Control", "Programming", 18,
                    "Branching, merging and pull requests without the panic." },
            new Object[] { "UX Design Basics", "Design", 14,
                    "User research, wireframes and usability testing for non-designers." },
            new Object[] { "Time Management", "Soft Skills", 35,
                    "Prioritise, plan your week and protect time for deep work." },
            new Object[] { "Machine Learning Intro", "Data", 10,
                    "Supervised learning, evaluation and the pitfalls beginners hit." });

    /** Titles the frontend ships a picture for (see tools/fetch_course_images.py). */
    private static final Set<String> TITLES_WITH_IMAGES = Set.of("Docker", "Docker Essentials", "Old Mainframe",
            "Legacy Systems");

    /** course title -> status -> usernames, to make the dashboard and request queue look lived-in. */
    private static final Map<String, Map<EnrollmentStatus, List<String>>> DEMO_ENROLLMENTS = Map.of(
            "Python for Data Analysis", Map.of(
                    EnrollmentStatus.APPROVED, List.of("aarav.sharma", "diya.patel", "rohan.mehta", "ananya.iyer", "vivaan.gupta"),
                    EnrollmentStatus.PENDING, List.of("kavya.nair", "arjun.reddy"),
                    EnrollmentStatus.REJECTED, List.of("meera.joshi")),
            "React Fundamentals", Map.of(
                    EnrollmentStatus.APPROVED, List.of("ishaan.verma", "saanvi.rao", "aditya.singh"),
                    EnrollmentStatus.PENDING, List.of("neha.kapoor", "karan.malhotra", "pooja.desai")),
            "Cloud Computing with AWS", Map.of(
                    EnrollmentStatus.APPROVED, List.of("rahul.bose", "sneha.kulkarni", "vikram.chauhan", "priya.menon"),
                    EnrollmentStatus.PENDING, List.of("siddharth.jain")),
            "Leadership for New Managers", Map.of(
                    EnrollmentStatus.APPROVED, List.of("tanvi.bhatt", "manish.agarwal", "riya.banerjee", "harsh.vardhan"),
                    EnrollmentStatus.PENDING, List.of("nisha.pandey", "devansh.trivedi")),
            "Machine Learning Intro", Map.of(
                    EnrollmentStatus.APPROVED, List.of("shreya.ghosh", "aarav.sharma"),
                    EnrollmentStatus.PENDING, List.of("diya.patel", "rohan.mehta", "ananya.iyer", "vivaan.gupta", "kavya.nair")),
            "Agile & Scrum Essentials", Map.of(
                    EnrollmentStatus.APPROVED, List.of("arjun.reddy", "meera.joshi", "ishaan.verma", "saanvi.rao", "neha.kapoor", "karan.malhotra"),
                    EnrollmentStatus.REJECTED, List.of("pooja.desai")),
            "Data Privacy & GDPR", Map.of(
                    EnrollmentStatus.APPROVED, List.of("rahul.bose", "sneha.kulkarni", "vikram.chauhan", "priya.menon", "siddharth.jain", "tanvi.bhatt", "manish.agarwal"),
                    EnrollmentStatus.PENDING, List.of("riya.banerjee")),
            "Effective Communication", Map.of(
                    EnrollmentStatus.PENDING, List.of("harsh.vardhan", "nisha.pandey", "devansh.trivedi", "shreya.ghosh")));

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, CourseRepository courseRepository,
                           EnrollmentRepository enrollmentRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers();
        seedCourses();
        seedEnrollments();
    }

    private void seedUsers() {
        if (!userRepository.existsByUsername("admin")) {
            userRepository.save(new User("admin", passwordEncoder.encode("admin123"), "System Admin", Role.ADMIN));
        }
        String hash = passwordEncoder.encode(EMPLOYEE_PASSWORD); // one BCrypt run, shared: all demo employees use the same password
        List<User> missing = EMPLOYEES.stream()
                .filter(e -> !userRepository.existsByUsername(e[0]))
                .map(e -> new User(e[0], hash, e[1], Role.EMPLOYEE))
                .toList();
        userRepository.saveAll(missing);
    }

    private void seedCourses() {
        Map<String, Course> byTitle = courseRepository.findAll().stream()
                .collect(Collectors.toMap(c -> c.getTitle().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));

        List<Course> created = COURSES.stream()
                .filter(c -> !byTitle.containsKey(((String) c[0]).toLowerCase(Locale.ROOT)))
                .map(c -> new Course((String) c[0], (String) c[3], (String) c[1], (Integer) c[2]))
                .toList();
        courseRepository.saveAll(created);

        if (courseRepository.findAll().stream().noneMatch(c -> c.getTitle().equals("Legacy Systems"))) {
            Course legacy = new Course("Legacy Systems", "Retired course", "Archive", 5);
            legacy.setActive(false);
            courseRepository.save(legacy);
        }

        // Attach the bundled picture to every course that has none and whose title we ship an image for.
        Set<String> known = Stream.concat(COURSES.stream().map(c -> (String) c[0]), TITLES_WITH_IMAGES.stream())
                .collect(Collectors.toSet());
        List<Course> needImage = courseRepository.findAll().stream()
                .filter(c -> c.getImageUrl() == null && known.contains(c.getTitle()))
                .peek(c -> c.setImageUrl("/course-images/" + slug(c.getTitle()) + ".jpg"))
                .toList();
        courseRepository.saveAll(needImage);
    }

    private void seedEnrollments() {
        Map<String, Course> courses = courseRepository.findAll().stream()
                .collect(Collectors.toMap(Course::getTitle, Function.identity(), (a, b) -> a));
        Map<String, User> users = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getUsername, Function.identity()));

        List<Enrollment> toSave = new ArrayList<>();
        int[] minutesAgo = { 60 * 24 * 6 };
        DEMO_ENROLLMENTS.forEach((title, byStatus) -> {
            Course course = courses.get(title);
            if (course == null) {
                return;
            }
            long approved = enrollmentRepository.countByCourseAndStatus(course, EnrollmentStatus.APPROVED);
            for (EnrollmentStatus status : EnrollmentStatus.values()) {
                for (String username : byStatus.getOrDefault(status, List.of())) {
                    User user = users.get(username);
                    if (user == null || enrollmentRepository.existsByUserAndCourse(user, course)) {
                        continue;
                    }
                    if (status == EnrollmentStatus.APPROVED) {
                        if (approved >= course.getCapacity()) {
                            continue;
                        }
                        approved++;
                    }
                    Enrollment e = new Enrollment(user, course);
                    e.setRequestedAt(LocalDateTime.now().minusMinutes(minutesAgo[0] -= 97));
                    if (status != EnrollmentStatus.PENDING) {
                        e.decide(status, "admin");
                    }
                    toSave.add(e);
                }
            }
        });
        enrollmentRepository.saveAll(toSave);
    }

    /** "Agile & Scrum Essentials" -> "agile-and-scrum-essentials" */
    static String slug(String title) {
        return title.toLowerCase(Locale.ROOT).replace("&", "and").replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
