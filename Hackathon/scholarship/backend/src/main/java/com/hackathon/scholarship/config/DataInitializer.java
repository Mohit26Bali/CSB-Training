package com.hackathon.scholarship.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.scholarship.entity.Application;
import com.hackathon.scholarship.entity.ApplicationStatus;
import com.hackathon.scholarship.entity.Role;
import com.hackathon.scholarship.entity.ScholarshipProgram;
import com.hackathon.scholarship.entity.User;
import com.hackathon.scholarship.repo.ApplicationRepository;
import com.hackathon.scholarship.repo.ScholarshipProgramRepository;
import com.hackathon.scholarship.repo.UserRepository;
import com.hackathon.scholarship.service.EligibilityCalculator;

/**
 * Seeds demo users, scholarship programmes and a believable set of applications. Idempotent: existing users are left
 * alone, programmes are only created when there are none, and applications only when there are none.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private static final List<String[]> ADMINS = List.of(
            new String[] { "scholarship", "Dr. Lakshmi Narayan" },
            new String[] { "meena.krishnan", "Meena Krishnan" });

    /** username, full name, course, year of study */
    private static final List<String[]> STUDENTS = List.of(
            new String[] { "aarav.sharma", "Aarav Sharma", "B.Tech Computer Science", "3" },
            new String[] { "diya.patel", "Diya Patel", "B.Tech Electronics", "2" },
            new String[] { "rohan.mehta", "Rohan Mehta", "B.Com Honours", "3" },
            new String[] { "ananya.iyer", "Ananya Iyer", "B.Sc Physics", "4" },
            new String[] { "vivaan.gupta", "Vivaan Gupta", "B.Tech Mechanical", "1" },
            new String[] { "kavya.nair", "Kavya Nair", "B.Sc Biotechnology", "2" },
            new String[] { "arjun.reddy", "Arjun Reddy", "BBA", "3" },
            new String[] { "meera.joshi", "Meera Joshi", "B.A. Economics", "2" },
            new String[] { "ishaan.verma", "Ishaan Verma", "B.Tech Civil", "4" },
            new String[] { "saanvi.rao", "Saanvi Rao", "B.Sc Mathematics", "1" },
            new String[] { "aditya.singh", "Aditya Singh", "B.Tech Information Technology", "2" },
            new String[] { "karan.malhotra", "Karan Malhotra", "B.Des Product Design", "3" },
            new String[] { "pooja.desai", "Pooja Desai", "B.Sc Chemistry", "3" },
            new String[] { "tanvi.bhatt", "Tanvi Bhatt", "B.Tech Computer Science", "1" });

    private static final List<String> APPROVAL_REASONS = List.of(
            "Strong academic record and clear financial need.",
            "Meets every criterion; the statement was convincing.",
            "Excellent grades. The family income is well within the limit.");

    private static final List<String> REJECTION_REASONS = List.of(
            "CGPA is below the minimum for this scholarship.",
            "Family income is above the limit for this scholarship.",
            "Other applicants had a stronger case this round.");

    private static final List<String> STATEMENTS = List.of(
            "I am the first in my family to attend university and this support would let me focus on my studies instead of part-time work.",
            "I lead our college project club and want to continue building open-source tools for local schools.",
            "My results have improved every semester. The scholarship would cover my lab and exam fees for the coming year.",
            "I plan to pursue research after graduation and need help with the cost of books and travel to a conference.");

    private final UserRepository users;
    private final ScholarshipProgramRepository programs;
    private final ApplicationRepository applications;
    private final PasswordEncoder encoder;

    public DataInitializer(UserRepository users, ScholarshipProgramRepository programs,
                           ApplicationRepository applications, PasswordEncoder encoder) {
        this.users = users;
        this.programs = programs;
        this.applications = applications;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers(ADMINS, Role.SCHOLARSHIP_ADMIN, "admin123");
        seedUsers(STUDENTS.stream().map(s -> new String[] { s[0], s[1] }).toList(), Role.STUDENT, "student123");
        if (programs.count() == 0) {
            seedPrograms();
        }
        if (applications.count() == 0) {
            seedApplications();
        }
    }

    private void seedUsers(List<String[]> people, Role role, String password) {
        String hash = encoder.encode(password);
        users.saveAll(people.stream().filter(p -> !users.existsByUsername(p[0]))
                .map(p -> new User(p[0], hash, p[1], role)).toList());
    }

    private void seedPrograms() {
        LocalDate today = LocalDate.now();
        ScholarshipProgram research = new ScholarshipProgram("Research Starter Grant",
                "Seed money for first research projects. Not open for applications at the moment.",
                bd(35000), today.plusDays(90), bd(8.0), bd(1200000));
        research.update(research.getName(), research.getDescription(), research.getAmount(), research.getDeadline(),
                research.getMinGpa(), research.getMaxFamilyIncome(), false);
        programs.saveAll(List.of(
                new ScholarshipProgram("Merit Excellence Scholarship",
                        "For students with outstanding academic results, regardless of discipline.",
                        bd(50000), today.plusDays(45), bd(8.5), bd(1500000)),
                new ScholarshipProgram("Women in STEM Grant",
                        "Supports women studying science, technology, engineering or mathematics.",
                        bd(75000), today.plusDays(30), bd(7.5), bd(1000000)),
                new ScholarshipProgram("First-Generation Learner Award",
                        "For students who are the first in their family to attend university.",
                        bd(40000), today.plusDays(60), bd(7.0), bd(500000)),
                new ScholarshipProgram("Rural Talent Fellowship",
                        "Helps promising students from rural districts with tuition and living costs.",
                        bd(60000), today.plusDays(4), bd(7.0), bd(400000)),
                new ScholarshipProgram("Sports Achievers Bursary",
                        "For students who represent the college at state level or above.",
                        bd(30000), today.plusDays(20), bd(6.5), bd(800000)),
                new ScholarshipProgram("Alumni Legacy Scholarship",
                        "Funded by alumni donations. This year's round has closed.",
                        bd(100000), today.minusDays(10), bd(9.0), bd(2000000)),
                research));
    }

    private void seedApplications() {
        Random random = new Random(5);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<ScholarshipProgram> openOrClosed = programs.findAll().stream().filter(ScholarshipProgram::isActive).toList();
        List<String> admins = ADMINS.stream().map(a -> a[0]).toList();
        List<Application> toSave = new ArrayList<>();

        for (String[] row : STUDENTS) {
            User student = users.findByUsername(row[0]).orElseThrow();
            // each student has one academic and financial profile, reused for every scholarship they apply to
            BigDecimal gpa = BigDecimal.valueOf(64 + random.nextInt(33), 1).setScale(2); // 6.4 to 9.6, exact (no double rounding)
            BigDecimal income = BigDecimal.valueOf(150000 + random.nextInt(1600) * 1000L).setScale(2);

            List<ScholarshipProgram> choices = new ArrayList<>(openOrClosed);
            Collections.shuffle(choices, random);
            int count = 2 + random.nextInt(3);
            for (ScholarshipProgram program : choices.subList(0, Math.min(count, choices.size()))) {
                boolean meets = EligibilityCalculator.meetsCriteria(gpa, income, program);
                Application a = new Application(student, program, gpa, income, row[2], Integer.parseInt(row[3]),
                        STATEMENTS.get(random.nextInt(STATEMENTS.size())),
                        EligibilityCalculator.score(gpa, income, program), meets);

                boolean closed = program.getDeadline().isBefore(today);
                LocalDateTime submitted = closed
                        ? program.getDeadline().minusDays(5 + random.nextInt(25)).atTime(10, random.nextInt(60))
                        : now.minusDays(1 + random.nextInt(20)).withHour(9 + random.nextInt(8)).withMinute(random.nextInt(60));
                a.setSubmittedAt(submitted);

                // closed rounds are fully decided; open ones are roughly half still waiting
                if (closed || random.nextInt(100) < 45) {
                    boolean approve = meets && random.nextInt(100) < 75;
                    a.decide(approve ? ApplicationStatus.APPROVED : ApplicationStatus.REJECTED,
                            admins.get(random.nextInt(admins.size())),
                            approve ? APPROVAL_REASONS.get(random.nextInt(APPROVAL_REASONS.size()))
                                    : meets ? REJECTION_REASONS.get(2)
                                            : REJECTION_REASONS.get(random.nextInt(2)));
                    LocalDateTime decided = submitted.plusDays(2 + random.nextInt(8));
                    a.setDecidedAt(decided.isAfter(now) ? now : decided);
                }
                toSave.add(a);
            }
        }
        applications.saveAll(toSave);
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(2);
    }
}
