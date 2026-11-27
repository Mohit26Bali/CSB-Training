package com.hackathon.referral.config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.referral.entity.Job;
import com.hackathon.referral.entity.JobStatus;
import com.hackathon.referral.entity.Referral;
import com.hackathon.referral.entity.ReferralStatus;
import com.hackathon.referral.entity.Role;
import com.hackathon.referral.entity.User;
import com.hackathon.referral.repo.JobRepository;
import com.hackathon.referral.repo.ReferralRepository;
import com.hackathon.referral.repo.UserRepository;

/**
 * Seeds demo users, a set of jobs (published, one draft, one closed) and referrals at every stage of the pipeline.
 * Idempotent: existing users are left alone, jobs and referrals are only created when there are none.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private static final List<String[]> RECRUITERS = List.of(
            new String[] { "recruiter", "Shalini Bhatia" },
            new String[] { "anil.kapoor", "Anil Kapoor" });

    private static final List<String[]> EMPLOYEES = List.of(
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
            new String[] { "karan.malhotra", "Karan Malhotra" });

    /** title, department, location, description, skills, status */
    private static final List<Object[]> JOBS = List.of(
            new Object[] { "Senior Java Developer", "Engineering", "Bengaluru",
                    "Design and build the services behind our payments platform. You will own features end to end, review code and mentor two junior developers.",
                    List.of("Java", "Spring Boot", "MySQL", "Microservices"), JobStatus.PUBLISHED },
            new Object[] { "React Frontend Engineer", "Engineering", "Hyderabad",
                    "Build accessible, fast interfaces for our customer portal with React and TypeScript, working closely with designers.",
                    List.of("React", "TypeScript", "CSS", "Testing"), JobStatus.PUBLISHED },
            new Object[] { "Data Analyst", "Analytics", "Pune",
                    "Turn product and sales data into dashboards and decisions. SQL every day, Python when needed.",
                    List.of("SQL", "Python", "Tableau"), JobStatus.PUBLISHED },
            new Object[] { "DevOps Engineer", "Engineering", "Chennai",
                    "Run our CI/CD pipelines and Kubernetes clusters, and make deployments boring.",
                    List.of("Docker", "Kubernetes", "AWS", "Terraform"), JobStatus.PUBLISHED },
            new Object[] { "QA Automation Engineer", "Quality", "Bengaluru",
                    "Own the automated regression suite and help developers test earlier.",
                    List.of("Selenium", "Java", "CI/CD"), JobStatus.PUBLISHED },
            new Object[] { "Product Designer", "Design", "Mumbai",
                    "Lead research and design for our mobile app, from first sketch to shipped feature.",
                    List.of("Figma", "User research", "Prototyping"), JobStatus.PUBLISHED },
            new Object[] { "HR Business Partner", "People", "Gurugram",
                    "Partner with engineering leaders on hiring, growth and team health.",
                    List.of("Communication", "Recruiting", "Coaching"), JobStatus.PUBLISHED },
            new Object[] { "Technical Writer", "Documentation", "Remote",
                    "Write clear API documentation and guides that developers actually enjoy reading.",
                    List.of("Writing", "API docs", "Markdown"), JobStatus.PUBLISHED },
            new Object[] { "Cloud Architect", "Engineering", "Bengaluru",
                    "Define our multi-cloud strategy. This role is being finalised and is not open yet.",
                    List.of("AWS", "Azure", "Terraform", "Security"), JobStatus.DRAFT },
            new Object[] { "Marketing Associate", "Marketing", "Mumbai",
                    "Run campaigns and content calendars. This position has been filled.",
                    List.of("SEO", "Content", "Analytics"), JobStatus.CLOSED });

    private static final List<String[]> CANDIDATES = List.of(
            new String[] { "Neelam Choudhary", "Bengaluru" }, new String[] { "Rajat Saxena", "Delhi" },
            new String[] { "Fatima Sheikh", "Hyderabad" }, new String[] { "Gaurav Tiwari", "Pune" },
            new String[] { "Lakshmi Venkatesh", "Chennai" }, new String[] { "Harpreet Kaur", "Chandigarh" },
            new String[] { "Imran Qureshi", "Lucknow" }, new String[] { "Sonal Deshmukh", "Nagpur" },
            new String[] { "Abhishek Banerjee", "Kolkata" }, new String[] { "Divya Krishnan", "Kochi" },
            new String[] { "Tarun Bansal", "Jaipur" }, new String[] { "Rekha Pillai", "Thiruvananthapuram" },
            new String[] { "Sandeep Yadav", "Indore" }, new String[] { "Nandini Rao", "Mysuru" },
            new String[] { "Mohit Arora", "Gurugram" }, new String[] { "Shruti Kulkarni", "Pune" },
            new String[] { "Vikas Pandey", "Bhopal" }, new String[] { "Anjali Menon", "Kochi" },
            new String[] { "Rahul Dutta", "Kolkata" }, new String[] { "Preeti Sinha", "Patna" },
            new String[] { "Naveen Gowda", "Bengaluru" }, new String[] { "Swati Joshi", "Mumbai" },
            new String[] { "Kunal Mehra", "Delhi" }, new String[] { "Bhavna Shah", "Ahmedabad" },
            new String[] { "Dhruv Kapoor", "Noida" }, new String[] { "Aisha Khan", "Hyderabad" },
            new String[] { "Pranav Nambiar", "Chennai" }, new String[] { "Ritu Agarwal", "Kanpur" },
            new String[] { "Siddharth Iyer", "Coimbatore" }, new String[] { "Komal Thakur", "Surat" },
            new String[] { "Yash Jadhav", "Pune" }, new String[] { "Madhuri Rao", "Visakhapatnam" });

    private static final List<String> NOTES = List.of(
            "Worked with me for two years. Strong problem solver and a great teammate.",
            "Met at a conference. Very good with the exact stack in this role.",
            "Former classmate with a solid portfolio of open-source work.",
            "Led a similar project at their current company and is ready for a bigger scope.");

    private static final List<String> REJECTIONS = List.of(
            "Skills do not match the role closely enough right now.",
            "Salary expectations are above the band for this position.",
            "Another candidate was a stronger fit for this opening.");

    private final UserRepository users;
    private final JobRepository jobs;
    private final ReferralRepository referrals;
    private final PasswordEncoder encoder;

    public DataInitializer(UserRepository users, JobRepository jobs, ReferralRepository referrals, PasswordEncoder encoder) {
        this.users = users;
        this.jobs = jobs;
        this.referrals = referrals;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers(RECRUITERS, Role.RECRUITER, "recruiter123");
        seedUsers(EMPLOYEES, Role.EMPLOYEE, "employee123");
        if (jobs.count() == 0) {
            List<Job> seeded = seedJobs();
            if (referrals.count() == 0) {
                seedReferrals(seeded);
            }
        }
    }

    private void seedUsers(List<String[]> people, Role role, String password) {
        String hash = encoder.encode(password);
        users.saveAll(people.stream().filter(p -> !users.existsByUsername(p[0]))
                .map(p -> new User(p[0], hash, p[1], role)).toList());
    }

    @SuppressWarnings("unchecked")
    private List<Job> seedJobs() {
        Random random = new Random(9);
        LocalDateTime now = LocalDateTime.now();
        List<Job> result = new ArrayList<>();
        for (Object[] row : JOBS) {
            Job job = new Job((String) row[0], (String) row[1], (String) row[2], (String) row[3], (List<String>) row[4],
                    RECRUITERS.get(random.nextInt(RECRUITERS.size()))[0]);
            job.setCreatedAt(now.minusDays(20 + random.nextInt(50)));
            JobStatus status = (JobStatus) row[5];
            if (status != JobStatus.DRAFT) {
                job.publish();
                job.setPublishedAt(job.getCreatedAt().plusDays(1 + random.nextInt(4)));
            }
            if (status == JobStatus.CLOSED) {
                job.close();
            }
            result.add(job);
        }
        return jobs.saveAll(result);
    }

    private void seedReferrals(List<Job> seeded) {
        Random random = new Random(3);
        LocalDateTime now = LocalDateTime.now();
        List<Job> withReferrals = seeded.stream().filter(j -> j.getStatus() != JobStatus.DRAFT).toList();
        List<User> employees = EMPLOYEES.stream().map(e -> users.findByUsername(e[0]).orElseThrow()).toList();
        List<String> recruiters = RECRUITERS.stream().map(r -> r[0]).toList();
        Set<String> used = new HashSet<>(); // job id + email: keeps the demo data inside the unique constraint
        List<Referral> toSave = new ArrayList<>();

        for (int i = 0; i < CANDIDATES.size(); i++) {
            String[] candidate = CANDIDATES.get(i);
            Job job = withReferrals.get(random.nextInt(withReferrals.size()));
            String email = candidate[0].toLowerCase(Locale.ROOT).replace(' ', '.') + "@example.com";
            if (!used.add(job.getId() + email)) {
                continue;
            }
            User referrer = employees.get(random.nextInt(employees.size()));
            Referral r = new Referral(job, referrer, candidate[0], email, "+91 98" + (10000000 + random.nextInt(89999999)),
                    random.nextInt(100) < 60 ? "https://example.com/resumes/" + email.replace("@example.com", "") + ".pdf" : null,
                    NOTES.get(random.nextInt(NOTES.size())));
            LocalDateTime submitted = now.minusDays(1 + random.nextInt(35)).withHour(9 + random.nextInt(8)).withMinute(random.nextInt(60));
            r.setSubmittedAt(submitted);

            int roll = random.nextInt(100);
            ReferralStatus target = roll < 25 ? ReferralStatus.SUBMITTED : roll < 50 ? ReferralStatus.SCREENING
                    : roll < 70 ? ReferralStatus.INTERVIEW : roll < 80 ? ReferralStatus.SELECTED : ReferralStatus.REJECTED;
            if (job.getStatus() == JobStatus.CLOSED && target.ordinal() < ReferralStatus.SELECTED.ordinal()) {
                target = random.nextBoolean() ? ReferralStatus.SELECTED : ReferralStatus.REJECTED; // a closed job's referrals are finished
            }
            if (target != ReferralStatus.SUBMITTED) {
                String recruiter = recruiters.get(random.nextInt(recruiters.size()));
                ReferralStatus step = target == ReferralStatus.REJECTED
                        ? ReferralStatus.values()[random.nextInt(3)] // it was rejected from SUBMITTED, SCREENING or INTERVIEW
                        : target;
                // walk the pipeline so the history is consistent (each step is a legal transition)
                for (ReferralStatus s : new ReferralStatus[] { ReferralStatus.SCREENING, ReferralStatus.INTERVIEW, ReferralStatus.SELECTED }) {
                    if (target == ReferralStatus.REJECTED ? s.ordinal() > step.ordinal() : s.ordinal() > target.ordinal()) {
                        break;
                    }
                    r.moveTo(s, recruiter, null);
                }
                if (target == ReferralStatus.REJECTED) {
                    r.moveTo(ReferralStatus.REJECTED, recruiter, REJECTIONS.get(random.nextInt(REJECTIONS.size())));
                }
                LocalDateTime updated = submitted.plusDays(1 + random.nextInt(12));
                r.setUpdatedAt(updated.isAfter(now) ? now : updated);
            }
            toSave.add(r);
        }
        referrals.saveAll(toSave);
    }
}
