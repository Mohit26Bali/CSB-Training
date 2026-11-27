package com.hackathon.appointment.config;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.appointment.entity.Appointment;
import com.hackathon.appointment.entity.AppointmentStatus;
import com.hackathon.appointment.entity.Provider;
import com.hackathon.appointment.entity.Role;
import com.hackathon.appointment.entity.Slot;
import com.hackathon.appointment.entity.User;
import com.hackathon.appointment.repo.AppointmentRepository;
import com.hackathon.appointment.repo.ProviderRepository;
import com.hackathon.appointment.repo.SlotRepository;
import com.hackathon.appointment.repo.UserRepository;

/**
 * Seeds five providers, 30 customers, weekday slots from two weeks ago to two weeks ahead (mornings and early afternoons, no lunch hour)
 * and about 360 appointments: past ones, pending and confirmed ones ahead, and some cancellations that put the slot back on offer.
 * No customer has two live appointments at the same time. aarav.sharma has an upcoming confirmed appointment, a pending one,
 * a past one and a cancelled one. Idempotent: users only when missing, providers and slots only when there are none.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    /** username, full name, title, description, location, slot minutes */
    private static final List<String[]> PROVIDERS = List.of(
            new String[] { "dr.mehta", "Dr. Neha Mehta", "General physician", "Consultations for fever, infections, check-ups and prescriptions.", "Clinic 2, MG Road, Pune", "30" },
            new String[] { "dr.rao", "Dr. Arvind Rao", "Dentist", "Cleaning, fillings, root canals and braces consultations.", "Smile Dental, Koregaon Park, Pune", "30" },
            new String[] { "kavita.joshi", "Kavita Joshi", "Physiotherapist", "Back and neck pain, sports injuries and post-surgery rehabilitation.", "ActiveLife Studio, Baner, Pune", "45" },
            new String[] { "sanjay.kulkarni", "Sanjay Kulkarni", "Career counsellor", "Résumé reviews, interview practice and career planning sessions.", "Online or at 5th Floor, FC Road, Pune", "60" },
            new String[] { "ritu.bansal", "Ritu Bansal", "Nutritionist", "Diet plans for weight, diabetes, pregnancy and sports nutrition.", "Wellness Hub, Kalyani Nagar, Pune", "30" });

    private static final List<String[]> CUSTOMERS = List.of(
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
            new String[] { "swati.mishra", "Swati Mishra" }, new String[] { "rajesh.nambiar", "Rajesh Nambiar" },
            new String[] { "isha.agarwal", "Isha Agarwal" }, new String[] { "pranav.joshi", "Pranav Joshi" },
            new String[] { "lakshmi.venkat", "Lakshmi Venkat" }, new String[] { "yash.thakur", "Yash Thakur" });

    private static final List<String> REASONS = List.of("Routine check-up", "First consultation", "Follow-up visit", "Pain for the last few days",
            "Second opinion", "Annual review", "Need advice on a plan", "Report discussion");
    private static final List<String> CANCEL_REASONS = List.of("Something urgent came up", "Booked a different time", "Feeling better", "Travelling that day");
    private static final List<String> PROVIDER_REASONS = List.of("Provider is unavailable that day", "Emergency at the clinic", "Please rebook for next week");

    private final UserRepository users;
    private final ProviderRepository providers;
    private final SlotRepository slots;
    private final AppointmentRepository appointments;
    private final PasswordEncoder encoder;

    public DataInitializer(UserRepository users, ProviderRepository providers, SlotRepository slots, AppointmentRepository appointments, PasswordEncoder encoder) {
        this.users = users;
        this.providers = providers;
        this.slots = slots;
        this.appointments = appointments;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers(PROVIDERS, Role.SERVICE_PROVIDER, "provider123");
        seedUsers(CUSTOMERS, Role.CUSTOMER, "customer123");
        if (providers.count() == 0) {
            seedSlotsAndAppointments();
        }
    }

    private void seedUsers(List<String[]> people, Role role, String password) {
        String hash = encoder.encode(password);
        users.saveAll(people.stream().filter(p -> !users.existsByUsername(p[0]))
                .map(p -> new User(p[0], hash, p[1], role)).toList());
    }

    private static List<LocalTime> startTimes(int minutes) {
        List<LocalTime> starts = new ArrayList<>();
        for (LocalTime[] window : new LocalTime[][] { { LocalTime.of(9, 0), LocalTime.of(12, 0) }, { LocalTime.of(14, 0), LocalTime.of(16, 0) } }) {
            for (LocalTime t = window[0]; !t.plusMinutes(minutes).isAfter(window[1]); t = t.plusMinutes(minutes)) {
                starts.add(t);
            }
        }
        return starts;
    }

    /** Customers' live appointments so far, to keep them from overlapping. */
    private static boolean busy(Map<String, List<LocalDateTime[]>> live, String username, Slot slot) {
        return live.getOrDefault(username, List.of()).stream().anyMatch(r -> r[0].isBefore(slot.getEndTime()) && slot.getStartTime().isBefore(r[1]));
    }

    private void seedSlotsAndAppointments() {
        Random random = new Random(20);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<User> customers = users.findAll().stream().filter(u -> u.getRole() == Role.CUSTOMER).sorted((a, b) -> a.getUsername().compareTo(b.getUsername())).toList();
        User aarav = customers.stream().filter(u -> u.getUsername().equals("aarav.sharma")).findFirst().orElseThrow();

        List<Slot> all = new ArrayList<>();
        Map<String, Provider> byUsername = new HashMap<>();
        for (String[] p : PROVIDERS) {
            Provider provider = providers.save(new Provider(users.findByUsername(p[0]).orElseThrow(), p[2], p[3], p[4]));
            byUsername.put(p[0], provider);
            List<LocalTime> starts = startTimes(Integer.parseInt(p[5]));
            for (LocalDate d = today.minusDays(14); !d.isAfter(today.plusDays(14)); d = d.plusDays(1)) {
                if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    continue;
                }
                for (LocalTime t : starts) {
                    all.add(new Slot(provider, d.atTime(t), d.atTime(t).plusMinutes(Integer.parseInt(p[5]))));
                }
            }
        }
        slots.saveAll(all);

        Map<String, List<LocalDateTime[]>> live = new HashMap<>();
        Set<Long> used = new HashSet<>();
        List<Appointment> made = new ArrayList<>();

        // aarav.sharma: one of each kind
        slotAt(all, byUsername.get("dr.mehta"), nextWeekday(today.plusDays(1)), LocalTime.of(10, 0)).ifPresent(s -> add(made, live, used, s, aarav, AppointmentStatus.CONFIRMED, "dr.mehta", "Annual review", random, now));
        slotAt(all, byUsername.get("dr.rao"), nextWeekday(today.plusDays(3)), LocalTime.of(11, 0)).ifPresent(s -> add(made, live, used, s, aarav, AppointmentStatus.PENDING, null, "Tooth sensitivity", random, now));
        slotAt(all, byUsername.get("dr.mehta"), previousWeekday(today.minusDays(6)), LocalTime.of(9, 30)).ifPresent(s -> add(made, live, used, s, aarav, AppointmentStatus.CONFIRMED, "dr.mehta", "Fever and cough", random, now));
        slotAt(all, byUsername.get("ritu.bansal"), nextWeekday(today.plusDays(5)), LocalTime.of(14, 0)).ifPresent(s -> add(made, live, used, s, aarav, AppointmentStatus.CANCELLED, "aarav.sharma", "Diet plan", random, now));

        for (Slot slot : all) {
            if (used.contains(slot.getId()) || slot.getProvider().getUser().getUsername().equals("sanjay.kulkarni") && random.nextInt(3) == 0) {
                continue;
            }
            boolean past = slot.getStartTime().isBefore(now);
            if (random.nextInt(100) >= (past ? 55 : 38)) {
                continue;
            }
            User customer = customers.get(1 + random.nextInt(customers.size() - 1));
            int roll = random.nextInt(100);
            AppointmentStatus status = past ? (roll < 86 ? AppointmentStatus.CONFIRMED : AppointmentStatus.CANCELLED)
                    : (roll < 38 ? AppointmentStatus.PENDING : roll < 90 ? AppointmentStatus.CONFIRMED : AppointmentStatus.CANCELLED);
            if (status != AppointmentStatus.CANCELLED && busy(live, customer.getUsername(), slot)) {
                continue;
            }
            String by = status == AppointmentStatus.CANCELLED ? (random.nextBoolean() ? customer.getUsername() : slot.getProvider().getUser().getUsername())
                    : slot.getProvider().getUser().getUsername();
            add(made, live, used, slot, customer, status, by, REASONS.get(random.nextInt(REASONS.size())), random, now);
        }
        appointments.saveAll(made);
    }

    private static LocalDate nextWeekday(LocalDate d) {
        LocalDate day = d;
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.plusDays(1);
        }
        return day;
    }

    private static LocalDate previousWeekday(LocalDate d) {
        LocalDate day = d;
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.minusDays(1);
        }
        return day;
    }

    private static java.util.Optional<Slot> slotAt(List<Slot> all, Provider provider, LocalDate day, LocalTime time) {
        return all.stream().filter(s -> s.getProvider() == provider && s.getStartTime().equals(day.atTime(time))).findFirst();
    }

    private void add(List<Appointment> made, Map<String, List<LocalDateTime[]>> live, Set<Long> used, Slot slot, User customer, AppointmentStatus status,
                     String by, String reason, Random random, LocalDateTime now) {
        Appointment a = new Appointment(slot, customer, reason);
        LocalDateTime created = slot.getStartTime().minusDays(1 + random.nextInt(8)).withMinute(random.nextInt(60));
        if (created.isAfter(now)) {
            created = now.minusHours(1 + random.nextInt(30));
        }
        String why = by != null && by.equals(customer.getUsername()) ? CANCEL_REASONS.get(random.nextInt(CANCEL_REASONS.size()))
                : PROVIDER_REASONS.get(random.nextInt(PROVIDER_REASONS.size()));
        a.forceState(status, created, by, why);
        made.add(a);
        if (status != AppointmentStatus.CANCELLED) {
            used.add(slot.getId());
            live.computeIfAbsent(customer.getUsername(), k -> new ArrayList<>()).add(new LocalDateTime[] { slot.getStartTime(), slot.getEndTime() });
        }
    }
}
