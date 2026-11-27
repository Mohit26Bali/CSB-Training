package com.hackathon.helpdesk.config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.helpdesk.entity.Category;
import com.hackathon.helpdesk.entity.Priority;
import com.hackathon.helpdesk.entity.Role;
import com.hackathon.helpdesk.entity.Ticket;
import com.hackathon.helpdesk.entity.TicketComment;
import com.hackathon.helpdesk.entity.TicketStatus;
import com.hackathon.helpdesk.entity.User;
import com.hackathon.helpdesk.repo.TicketCommentRepository;
import com.hackathon.helpdesk.repo.TicketRepository;
import com.hackathon.helpdesk.repo.UserRepository;

/**
 * Seeds three support agents, 30 employees and 36 tickets in every state: open and unassigned (some already past their SLA),
 * in progress, resolved and closed, with comments and internal notes. aarav.sharma has one ticket in each state.
 * Idempotent: users only when missing, tickets only when there are none.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private static final List<String[]> AGENTS = List.of(
            new String[] { "support", "Nandini Krishnan" },
            new String[] { "sanjay.rao", "Sanjay Rao" },
            new String[] { "fatima.sheikh", "Fatima Sheikh" });

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
            new String[] { "swati.mishra", "Swati Mishra" }, new String[] { "rajesh.nambiar", "Rajesh Nambiar" },
            new String[] { "isha.agarwal", "Isha Agarwal" }, new String[] { "pranav.joshi", "Pranav Joshi" },
            new String[] { "lakshmi.venkat", "Lakshmi Venkat" }, new String[] { "yash.thakur", "Yash Thakur" });

    /** title, description, category, priority, resolution */
    private static final List<Object[]> ISSUES = List.of(
            new Object[] { "Laptop will not boot after Windows update", "Stuck on the spinning logo since this morning's update. I have a client demo at 3 pm.", Category.HARDWARE, Priority.CRITICAL, "Rolled back the update from recovery mode and paused updates until the patched build arrives." },
            new Object[] { "VPN keeps disconnecting every few minutes", "Working from home. GlobalProtect drops about every 5 minutes and I lose my remote desktop session.", Category.NETWORK, Priority.HIGH, "Home router had a conflicting MTU. Set it to 1400 and moved the client to the new gateway." },
            new Object[] { "Cannot access the shared finance drive", "Getting 'access denied' on the Finance share since moving teams last week.", Category.ACCESS, Priority.MEDIUM, "Added the user to the Finance-Read group after manager approval." },
            new Object[] { "Outlook not syncing new emails", "Inbox stopped updating on my desktop. Webmail is fine and my phone shows new mail.", Category.EMAIL, Priority.MEDIUM, "Rebuilt the Outlook profile and re-indexed the mailbox." },
            new Object[] { "Install IntelliJ IDEA Ultimate", "Need the Ultimate edition for the Spring project. Licence is approved by my manager.", Category.SOFTWARE, Priority.LOW, "Installed from the software centre and assigned a licence seat." },
            new Object[] { "Monitor flickers on the second display", "The external monitor flickers every few seconds, worse when I plug in the charger.", Category.HARDWARE, Priority.LOW, "Replaced the HDMI cable and updated the graphics driver." },
            new Object[] { "Wi-Fi extremely slow on the 4th floor", "Speed tests show 2 Mbps in the east wing. Video calls are unusable.", Category.NETWORK, Priority.HIGH, "Access point AP-4E was overloaded; rebalanced clients and added a second AP." },
            new Object[] { "Password reset link never arrives", "I requested a reset for the HR portal three times and no email came.", Category.ACCESS, Priority.MEDIUM, "The mail was quarantined by the spam filter. Released it and whitelisted the sender." },
            new Object[] { "Teams crashes when sharing my screen", "Teams closes without an error as soon as I start screen sharing.", Category.SOFTWARE, Priority.MEDIUM, "Cleared the Teams cache and reinstalled the latest build." },
            new Object[] { "Printer on floor 2 shows offline", "Everyone on floor 2 gets 'printer offline'. The printer itself looks fine.", Category.HARDWARE, Priority.MEDIUM, "Print server queue was stuck. Restarted the spooler and cleared the queue." },
            new Object[] { "Need access to the staging database", "I joined the payments team and need read access to the staging MySQL for debugging.", Category.ACCESS, Priority.LOW, "Granted read-only access to the staging schema for 90 days." },
            new Object[] { "Shared mailbox missing from Outlook", "support-team@ no longer shows up in my folder list since Monday.", Category.EMAIL, Priority.LOW, "Re-added the Full Access permission and the automapping." },
            new Object[] { "Excel macro security warning blocks reports", "The month-end macro workbook now refuses to run. Finance close is tomorrow.", Category.SOFTWARE, Priority.HIGH, "Signed the workbook with the company certificate and added the share to trusted locations." },
            new Object[] { "Keyboard keys sticking", "The E and R keys stick and double-type. Spilled coffee last week.", Category.HARDWARE, Priority.LOW, "Replaced the keyboard from stock." },
            new Object[] { "Cannot connect to meeting room display", "The Wireless Presentation display in Room Everest does not show up as an option.", Category.NETWORK, Priority.MEDIUM, "The display was on the guest VLAN. Moved it to the corporate VLAN." },
            new Object[] { "Locked out of my account after a phone change", "New phone and the authenticator codes do not work. Locked out of everything.", Category.ACCESS, Priority.CRITICAL, "Verified identity with the manager, reset MFA and enrolled the new phone." },
            new Object[] { "Mail rejected: mailbox full", "Senders say my mailbox is full but I have only used 30 percent in webmail.", Category.EMAIL, Priority.MEDIUM, "Archive mailbox was at quota. Raised the archive limit." },
            new Object[] { "Laptop battery drains in an hour", "Battery went from 100 to 20 percent in under an hour, even when idle.", Category.HARDWARE, Priority.MEDIUM, "Battery health was 38 percent. Replaced the battery." },
            new Object[] { "Install Docker Desktop", "Need Docker Desktop for local microservice testing.", Category.SOFTWARE, Priority.LOW, "Installed Docker Desktop with WSL2 and enabled virtualisation in the BIOS." },
            new Object[] { "Jira very slow since this morning", "Pages take over 30 seconds to load for the whole team.", Category.SOFTWARE, Priority.HIGH, "Jira's database connection pool was exhausted. Restarted the service and raised the pool size." },
            new Object[] { "Request a second monitor", "My manager approved a second monitor for the data team desk.", Category.HARDWARE, Priority.LOW, "Delivered a 24-inch monitor and a docking station." },
            new Object[] { "Conference calls echo badly", "Everyone says they hear an echo of themselves whenever I speak.", Category.NETWORK, Priority.LOW, "Switched to the headset and disabled the duplicate audio device." },
            new Object[] { "Phishing email received", "Got an email asking me to confirm my password through a link. I did not click it.", Category.EMAIL, Priority.HIGH, "Blocked the sender domain, pulled the message from all mailboxes and sent a reminder to staff." });

    private static final List<String> AGENT_REPLIES = List.of(
            "Thanks for reporting this. I am looking into it now and will update you shortly.",
            "Could you tell me when this first started, and whether anything changed on your machine around then?",
            "I have reproduced the problem on a test machine. A fix is being prepared.",
            "Please try restarting once more and let me know if the issue is still there.");

    private static final List<String> EMPLOYEE_REPLIES = List.of(
            "It started yesterday afternoon, right after I installed an update.",
            "Still happening this morning, nothing has changed on my side.",
            "Thank you, that worked for me now.");

    private static final List<String> INTERNAL_NOTES = List.of(
            "Seen on three other machines in the same department, probably the same root cause.",
            "Waiting for the vendor's patch. Do not close until confirmed with the user.",
            "Checked the logs: same error as the ticket from last week.");

    private final UserRepository users;
    private final TicketRepository tickets;
    private final TicketCommentRepository comments;
    private final PasswordEncoder encoder;

    public DataInitializer(UserRepository users, TicketRepository tickets, TicketCommentRepository comments, PasswordEncoder encoder) {
        this.users = users;
        this.tickets = tickets;
        this.comments = comments;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers(AGENTS, Role.SUPPORT_AGENT, "agent123");
        seedUsers(EMPLOYEES, Role.EMPLOYEE, "employee123");
        if (tickets.count() == 0) {
            seedTickets();
        }
    }

    private void seedUsers(List<String[]> people, Role role, String password) {
        String hash = encoder.encode(password);
        users.saveAll(people.stream().filter(p -> !users.existsByUsername(p[0]))
                .map(p -> new User(p[0], hash, p[1], role)).toList());
    }

    private void seedTickets() {
        Random random = new Random(14);
        LocalDateTime now = LocalDateTime.now();
        List<User> employees = EMPLOYEES.stream().map(e -> users.findByUsername(e[0]).orElseThrow()).toList();
        List<User> agents = AGENTS.stream().map(a -> users.findByUsername(a[0]).orElseThrow()).toList();

        // status of each of the 36 tickets: 7 open, 9 in progress, 7 resolved, 13 closed; aarav gets indexes 0, 7, 16, 23
        List<TicketStatus> plan = new ArrayList<>();
        for (int i = 0; i < 7; i++) plan.add(TicketStatus.OPEN);
        for (int i = 0; i < 9; i++) plan.add(TicketStatus.IN_PROGRESS);
        for (int i = 0; i < 7; i++) plan.add(TicketStatus.RESOLVED);
        for (int i = 0; i < 13; i++) plan.add(TicketStatus.CLOSED);
        int[] aaravAt = { 0, 7, 16, 23 };
        // hours since creation for the open tickets: a mix of on-track, at-risk and breached
        int[] openAges = { 1, 3, 20, 7, 30, 60, 2 };

        List<Ticket> toSave = new ArrayList<>();
        List<Object[]> pendingComments = new ArrayList<>(); // ticket, kind
        for (int i = 0; i < plan.size(); i++) {
            Object[] issue = ISSUES.get(i % ISSUES.size());
            Priority priority = (Priority) issue[3];
            TicketStatus status = plan.get(i);
            boolean aarav = i == aaravAt[0] || i == aaravAt[1] || i == aaravAt[2] || i == aaravAt[3];
            User requester = aarav ? employees.get(0) : employees.get(1 + random.nextInt(employees.size() - 1));
            Ticket t = new Ticket((String) issue[0], (String) issue[1], (Category) issue[2], priority, requester);

            long age = switch (status) {
                case OPEN -> openAges[i];
                case IN_PROGRESS -> 2 + random.nextInt(40);
                case RESOLVED -> 24 + random.nextInt(96);
                case CLOSED -> 96 + random.nextInt(500);
            };
            t.startedAt(now.minusHours(age));
            if (status != TicketStatus.OPEN) {
                t.assignTo(agents.get(random.nextInt(agents.size())));
            }
            if (status == TicketStatus.IN_PROGRESS) {
                t.forceStatus(TicketStatus.IN_PROGRESS);
            }
            if (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED) {
                // some are fixed inside the SLA, a few miss it
                long took = random.nextInt(5) == 0 ? priority.getSlaHours() + 1 + random.nextInt(10) : 1 + random.nextInt(Math.max(1, priority.getSlaHours() - 1));
                t.forceStatus(status);
                t.setResolution((String) issue[4]);
                t.setResolvedAt(t.getCreatedAt().plusHours(took));
                if (status == TicketStatus.CLOSED) {
                    t.setClosedAt(t.getResolvedAt().plusHours(6 + random.nextInt(60)));
                }
            }
            t.setUpdatedAt(now.minusHours(Math.max(0, age - 1)));
            toSave.add(t);
            if (status != TicketStatus.OPEN) {
                pendingComments.add(new Object[] { t, status });
            }
        }
        List<Ticket> saved = tickets.saveAll(toSave);

        List<TicketComment> thread = new ArrayList<>();
        for (Ticket t : saved) {
            if (t.getStatus() == TicketStatus.OPEN || t.getAssignee() == null) {
                continue;
            }
            LocalDateTime when = t.getCreatedAt().plusMinutes(25);
            thread.add(stamped(new TicketComment(t, t.getAssignee(), AGENT_REPLIES.get(random.nextInt(AGENT_REPLIES.size())), false), when));
            thread.add(stamped(new TicketComment(t, t.getRequester(), EMPLOYEE_REPLIES.get(random.nextInt(2)), false), when.plusMinutes(40)));
            if (random.nextBoolean()) {
                thread.add(stamped(new TicketComment(t, t.getAssignee(), INTERNAL_NOTES.get(random.nextInt(INTERNAL_NOTES.size())), true), when.plusMinutes(70)));
            }
            if (t.getStatus() == TicketStatus.RESOLVED || t.getStatus() == TicketStatus.CLOSED) {
                thread.add(stamped(new TicketComment(t, t.getRequester(), EMPLOYEE_REPLIES.get(2), false), t.getResolvedAt().plusMinutes(20)));
            }
        }
        comments.saveAll(thread);
    }

    private TicketComment stamped(TicketComment c, LocalDateTime when) {
        c.setCreatedAt(when);
        return c;
    }
}
