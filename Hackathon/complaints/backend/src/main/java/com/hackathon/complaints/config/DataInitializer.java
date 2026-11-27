package com.hackathon.complaints.config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.complaints.entity.Category;
import com.hackathon.complaints.entity.Complaint;
import com.hackathon.complaints.entity.ComplaintEvent;
import com.hackathon.complaints.entity.ComplaintStatus;
import com.hackathon.complaints.entity.EventType;
import com.hackathon.complaints.entity.Priority;
import com.hackathon.complaints.entity.Role;
import com.hackathon.complaints.entity.User;
import com.hackathon.complaints.repo.ComplaintEventRepository;
import com.hackathon.complaints.repo.ComplaintRepository;
import com.hackathon.complaints.repo.UserRepository;

/**
 * Seeds three agents, 30 customers and 36 complaints in every state, each with a timeline; resolved and closed ones
 * are mostly rated. aarav.sharma has one complaint in each state. Idempotent: users only when missing, complaints only when none exist.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private static final List<String[]> AGENTS = List.of(
            new String[] { "agent", "Kavita Rane" },
            new String[] { "kiran.bhat", "Kiran Bhat" },
            new String[] { "zoya.khan", "Zoya Khan" });

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

    /** subject, description, category, priority, resolution */
    private static final List<Object[]> ISSUES = List.of(
            new Object[] { "Charged twice for the same order", "My card was debited twice for order #48213 on the same minute. I only placed one order.", Category.BILLING, Priority.HIGH, "Confirmed a duplicate authorisation. The second charge has been reversed and will show on the statement in 3 to 5 days." },
            new Object[] { "Mixer grinder arrived with a cracked jar", "The jar was cracked when I opened the box. The outer carton was visibly dented.", Category.PRODUCT, Priority.MEDIUM, "Sent a replacement jar by courier, free of charge, and flagged the carton damage with the carrier." },
            new Object[] { "Delivery is ten days late", "The tracking page has said 'out for delivery' for three days. Nobody has called.", Category.DELIVERY, Priority.HIGH, "The parcel was stuck at a regional hub. Re-dispatched with priority and credited a delivery refund." },
            new Object[] { "Rude behaviour at the service counter", "The person at the Indiranagar counter was dismissive and refused to look at my bill.", Category.STAFF, Priority.MEDIUM, "Reviewed the incident with the store manager, who has spoken to the staff member. A written apology was sent." },
            new Object[] { "Refund not received after 3 weeks", "I returned the order on the 2nd and the pickup was confirmed. No refund yet.", Category.BILLING, Priority.URGENT, "The returned item was received but not scanned. Scanned it and processed the refund to the original card." },
            new Object[] { "Received the wrong colour", "I ordered the blue backpack and got a grey one.", Category.PRODUCT, Priority.LOW, "Arranged a pickup of the grey backpack and shipped the blue one." },
            new Object[] { "App crashes when I try to pay", "The app closes every time I tap 'Pay now' on version 5.2.", Category.SERVICE, Priority.HIGH, "Fixed in version 5.2.1. Walked the customer through the update and completed the order." },
            new Object[] { "Warranty claim rejected without a reason", "My claim for the faulty heater was rejected and the email gave no explanation.", Category.SERVICE, Priority.MEDIUM, "The rejection was a data entry error. Claim re-opened and approved; a technician visit is booked." },
            new Object[] { "Promised call-back never happened", "I was told twice that a senior would call me back within 24 hours.", Category.SERVICE, Priority.LOW, "Apologised and arranged a call with the team lead the same day; the call-back process has been tightened." },
            new Object[] { "Invoice shows the wrong GST number", "My company GST number is wrong on the invoice, so I cannot claim input credit.", Category.BILLING, Priority.MEDIUM, "Issued a corrected invoice with the right GST number." },
            new Object[] { "Package left with a neighbour without asking", "The courier left my parcel with someone I do not know. It cost me two days to find it.", Category.DELIVERY, Priority.LOW, "Raised the delivery instruction breach with the courier partner and added a delivery note to the account." },
            new Object[] { "Subscription renewed after I cancelled", "I cancelled my annual plan in March and was still billed this month.", Category.BILLING, Priority.URGENT, "Cancellation had not synced. Refunded the full renewal and confirmed cancellation by email." });

    private static final List<String> OPENING_NOTES = List.of(
            "I have picked this up and am checking the order history.",
            "Looking into this with the logistics team now.",
            "I have asked the billing team for the transaction details and will update you.",
            "Thanks for the detail. I am reproducing the problem on my side.");

    private static final List<String> FEEDBACK = List.of(
            "Quick and polite. Thank you.", "Solved, but it took longer than it should have.", "Very helpful, the refund arrived on time.",
            "Good outcome, though I had to chase once.", "Excellent follow-up.");

    private final UserRepository users;
    private final ComplaintRepository complaints;
    private final ComplaintEventRepository events;
    private final PasswordEncoder encoder;

    public DataInitializer(UserRepository users, ComplaintRepository complaints, ComplaintEventRepository events, PasswordEncoder encoder) {
        this.users = users;
        this.complaints = complaints;
        this.events = events;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers(AGENTS, Role.SUPPORT_AGENT, "agent123");
        seedUsers(CUSTOMERS, Role.CUSTOMER, "customer123");
        if (complaints.count() == 0) {
            seedComplaints();
        }
    }

    private void seedUsers(List<String[]> people, Role role, String password) {
        String hash = encoder.encode(password);
        users.saveAll(people.stream().filter(p -> !users.existsByUsername(p[0]))
                .map(p -> new User(p[0], hash, p[1], role)).toList());
    }

    private ComplaintEvent at(ComplaintEvent e, LocalDateTime when) {
        e.setCreatedAt(when);
        return e;
    }

    private void seedComplaints() {
        Random random = new Random(17);
        LocalDateTime now = LocalDateTime.now();
        List<User> customers = CUSTOMERS.stream().map(c -> users.findByUsername(c[0]).orElseThrow()).toList();
        List<User> agents = AGENTS.stream().map(a -> users.findByUsername(a[0]).orElseThrow()).toList();

        // 6 open, 8 in progress, 8 resolved, 14 closed; aarav gets indexes 0, 6, 14, 22
        List<ComplaintStatus> plan = new ArrayList<>();
        for (int i = 0; i < 6; i++) plan.add(ComplaintStatus.OPEN);
        for (int i = 0; i < 8; i++) plan.add(ComplaintStatus.IN_PROGRESS);
        for (int i = 0; i < 8; i++) plan.add(ComplaintStatus.RESOLVED);
        for (int i = 0; i < 14; i++) plan.add(ComplaintStatus.CLOSED);
        List<Integer> aaravAt = List.of(0, 6, 14, 22);
        int[] openAgesHours = { 5, 20, 52, 80, 3, 120 };

        List<ComplaintEvent> timeline = new ArrayList<>();
        for (int i = 0; i < plan.size(); i++) {
            Object[] issue = ISSUES.get(i % ISSUES.size());
            ComplaintStatus status = plan.get(i);
            User customer = aaravAt.contains(i) ? customers.get(0) : customers.get(1 + random.nextInt(customers.size() - 1));
            Complaint c = new Complaint((String) issue[0], (String) issue[1], (Category) issue[2], (Priority) issue[3], customer);
            LocalDateTime filed = switch (status) {
                case OPEN -> now.minusHours(openAgesHours[i]);
                case IN_PROGRESS -> now.minusHours(10 + random.nextInt(100));
                case RESOLVED -> now.minusDays(2 + random.nextInt(8));
                case CLOSED -> now.minusDays(12 + random.nextInt(60));
            };
            User agent = agents.get(random.nextInt(agents.size()));
            LocalDateTime resolved = status == ComplaintStatus.RESOLVED || status == ComplaintStatus.CLOSED
                    ? filed.plusHours(6 + random.nextInt(70)) : null;
            LocalDateTime closed = status == ComplaintStatus.CLOSED ? resolved.plusDays(1 + random.nextInt(4)) : null;
            c.forceState(status, filed, resolved, closed, resolved == null ? null : (String) issue[4]);
            if (status != ComplaintStatus.OPEN) {
                c.assignTo(agent);
                c.forceState(status, filed, resolved, closed, resolved == null ? null : (String) issue[4]);
            }
            if (i != 14 && ((status == ComplaintStatus.CLOSED && random.nextInt(10) < 8) || (status == ComplaintStatus.RESOLVED && random.nextInt(2) == 0))) { // aarav's resolved one stays unrated, so rating can be tried
                c.rate(3 + random.nextInt(3), FEEDBACK.get(random.nextInt(FEEDBACK.size())));
            }
            Complaint saved = complaints.save(c);
            timeline.add(at(new ComplaintEvent(saved, customer, EventType.FILED, ComplaintStatus.OPEN, null), filed));
            if (status != ComplaintStatus.OPEN) {
                timeline.add(at(new ComplaintEvent(saved, agent, EventType.ASSIGNED, null, "Assigned to " + agent.getFullname()), filed.plusMinutes(35)));
                timeline.add(at(new ComplaintEvent(saved, agent, EventType.STATUS_CHANGED, ComplaintStatus.IN_PROGRESS,
                        OPENING_NOTES.get(random.nextInt(OPENING_NOTES.size()))), filed.plusMinutes(50)));
            }
            if (resolved != null) {
                timeline.add(at(new ComplaintEvent(saved, agent, EventType.STATUS_CHANGED, ComplaintStatus.RESOLVED, (String) issue[4]), resolved));
            }
            if (closed != null) {
                timeline.add(at(new ComplaintEvent(saved, agent, EventType.STATUS_CHANGED, ComplaintStatus.CLOSED, null), closed));
            }
            if (saved.getRating() != null) {
                timeline.add(at(new ComplaintEvent(saved, customer, EventType.FEEDBACK, null, saved.getRating() + " of 5: " + saved.getFeedback()),
                        resolved.plusHours(3)));
            }
        }
        events.saveAll(timeline);
    }
}
