package com.hackathon.library.config;

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

import com.hackathon.library.entity.Book;
import com.hackathon.library.entity.Loan;
import com.hackathon.library.entity.Role;
import com.hackathon.library.entity.User;
import com.hackathon.library.repo.BookRepository;
import com.hackathon.library.repo.LoanRepository;
import com.hackathon.library.repo.UserRepository;

/**
 * Seeds librarians, 25 members, 36 books in seven categories and loans in every state: books out and overdue, reservations
 * waiting, returned books (some with fines) and one title with every copy held. aarav.sharma has one overdue book and one
 * reservation. Idempotent: users only when missing, books and loans only when there are none.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private static final int LOAN_DAYS = 14;
    private static final int FINE_PER_DAY = 5;

    private static final List<String[]> LIBRARIANS = List.of(
            new String[] { "librarian", "Meenakshi Iyer" },
            new String[] { "ramesh.pillai", "Ramesh Pillai" });

    private static final List<String[]> MEMBERS = List.of(
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
            new String[] { "swati.mishra", "Swati Mishra" });

    /** title, author, category, year, copies, description */
    private static final List<Object[]> BOOKS = List.of(
            new Object[] { "Clean Code", "Robert C. Martin", "Technology", 2008, 3, "A handbook of agile software craftsmanship: names, functions, comments and tests that keep code readable." },
            new Object[] { "The Pragmatic Programmer", "Andrew Hunt, David Thomas", "Technology", 1999, 1, "Practical advice on becoming a better developer, from debugging to automation." },
            new Object[] { "Designing Data-Intensive Applications", "Martin Kleppmann", "Technology", 2017, 2, "The big ideas behind reliable, scalable and maintainable data systems." },
            new Object[] { "Introduction to Algorithms", "Thomas H. Cormen", "Technology", 2009, 4, "The standard reference on algorithms, with proofs and pseudocode." },
            new Object[] { "Effective Java", "Joshua Bloch", "Technology", 2018, 3, "Ninety items on writing clear, correct and robust Java." },
            new Object[] { "Sapiens: A Brief History of Humankind", "Yuval Noah Harari", "History", 2011, 4, "How Homo sapiens came to rule the world, from the Stone Age to the present." },
            new Object[] { "India After Gandhi", "Ramachandra Guha", "History", 2007, 2, "The history of the world's largest democracy since independence." },
            new Object[] { "The Discovery of India", "Jawaharlal Nehru", "History", 1946, 2, "A sweeping account of India's past, written in prison." },
            new Object[] { "Guns, Germs, and Steel", "Jared Diamond", "History", 1997, 2, "Why some societies became dominant while others did not." },
            new Object[] { "A Brief History of Time", "Stephen Hawking", "Science", 1988, 3, "From the Big Bang to black holes, for the general reader." },
            new Object[] { "Cosmos", "Carl Sagan", "Science", 1980, 2, "A personal voyage through the universe and our place in it." },
            new Object[] { "The Selfish Gene", "Richard Dawkins", "Science", 1976, 2, "Evolution seen from the gene's point of view." },
            new Object[] { "Surely You're Joking, Mr. Feynman!", "Richard P. Feynman", "Science", 1985, 3, "Adventures of a curious character in physics and life." },
            new Object[] { "Astrophysics for People in a Hurry", "Neil deGrasse Tyson", "Science", 2017, 3, "The universe, explained in short chapters." },
            new Object[] { "Wings of Fire", "A. P. J. Abdul Kalam", "Biography", 1999, 5, "The autobiography of India's Missile Man." },
            new Object[] { "The Story of My Experiments with Truth", "M. K. Gandhi", "Biography", 1927, 3, "Gandhi's own account of his life and principles." },
            new Object[] { "Steve Jobs", "Walter Isaacson", "Biography", 2011, 3, "The exclusive biography of Apple's co-founder." },
            new Object[] { "Long Walk to Freedom", "Nelson Mandela", "Biography", 1994, 2, "Mandela's story, from village boy to president." },
            new Object[] { "Becoming", "Michelle Obama", "Biography", 2018, 3, "A memoir of family, work and public life." },
            new Object[] { "Atomic Habits", "James Clear", "Self-help", 2018, 5, "Small changes that compound into remarkable results." },
            new Object[] { "Deep Work", "Cal Newport", "Self-help", 2016, 3, "Rules for focused success in a distracted world." },
            new Object[] { "The 7 Habits of Highly Effective People", "Stephen R. Covey", "Self-help", 1989, 3, "Principles of personal and professional effectiveness." },
            new Object[] { "Ikigai", "Hector Garcia, Francesc Miralles", "Self-help", 2016, 4, "The Japanese secret to a long and happy life." },
            new Object[] { "Think and Grow Rich", "Napoleon Hill", "Self-help", 1937, 2, "Thirteen principles drawn from the habits of successful people." },
            new Object[] { "The God of Small Things", "Arundhati Roy", "Fiction", 1997, 3, "A family, a river and a tragedy in Kerala. Booker Prize winner." },
            new Object[] { "The White Tiger", "Aravind Adiga", "Fiction", 2008, 3, "A darkly funny tale of a village boy turned entrepreneur." },
            new Object[] { "Midnight's Children", "Salman Rushdie", "Fiction", 1981, 2, "Born at the stroke of independence, Saleem's life mirrors the nation's." },
            new Object[] { "The Alchemist", "Paulo Coelho", "Fiction", 1988, 5, "A shepherd's journey to find his personal legend." },
            new Object[] { "1984", "George Orwell", "Fiction", 1949, 4, "A chilling vision of surveillance and control." },
            new Object[] { "To Kill a Mockingbird", "Harper Lee", "Fiction", 1960, 3, "Justice and innocence in a small Southern town." },
            new Object[] { "The Kite Runner", "Khaled Hosseini", "Fiction", 2003, 3, "Friendship, betrayal and redemption in Afghanistan." },
            new Object[] { "Malgudi Days", "R. K. Narayan", "Fiction", 1943, 3, "Gentle stories from a small South Indian town." },
            new Object[] { "Harry Potter and the Philosopher's Stone", "J. K. Rowling", "Children", 1997, 5, "A boy discovers he is a wizard." },
            new Object[] { "The Jungle Book", "Rudyard Kipling", "Children", 1894, 3, "Mowgli and the animals of the Indian jungle." },
            new Object[] { "Charlie and the Chocolate Factory", "Roald Dahl", "Children", 1964, 3, "Five children, one golden ticket and a very strange factory." },
            new Object[] { "The Adventures of Tom Sawyer", "Mark Twain", "Children", 1876, 2, "Mischief and adventure along the Mississippi." },
            new Object[] { "Panchatantra Tales", "Vishnu Sharma", "Children", 300, 3, "Ancient fables of animals and wisdom." });

    private static final String[] TITLES_ISSUED_TO_AARAV = { "Clean Code", "Sapiens: A Brief History of Humankind" };

    private final UserRepository users;
    private final BookRepository books;
    private final LoanRepository loans;
    private final PasswordEncoder encoder;

    public DataInitializer(UserRepository users, BookRepository books, LoanRepository loans, PasswordEncoder encoder) {
        this.users = users;
        this.books = books;
        this.loans = loans;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers(LIBRARIANS, Role.LIBRARIAN, "librarian123");
        seedUsers(MEMBERS, Role.MEMBER, "member123");
        if (books.count() == 0) {
            List<Book> saved = seedBooks();
            if (loans.count() == 0) {
                seedLoans(saved);
            }
        }
    }

    private void seedUsers(List<String[]> people, Role role, String password) {
        String hash = encoder.encode(password);
        users.saveAll(people.stream().filter(p -> !users.existsByUsername(p[0]))
                .map(p -> new User(p[0], hash, p[1], role)).toList());
    }

    /** ISBN-13 with a correct check digit, built from a running number so every book has its own. */
    private static String isbn(int index) {
        String body = "978" + String.format("%09d", 100000000 + index * 7919);
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += (body.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return body + ((10 - sum % 10) % 10);
    }

    private List<Book> seedBooks() {
        List<Book> list = new ArrayList<>();
        for (int i = 0; i < BOOKS.size(); i++) {
            Object[] row = BOOKS.get(i);
            list.add(new Book((String) row[0], (String) row[1], isbn(i), (String) row[2], (String) row[5], (Integer) row[3], (Integer) row[4]));
        }
        return books.saveAll(list);
    }

    private void seedLoans(List<Book> catalogue) {
        Random random = new Random(16);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<User> members = MEMBERS.stream().map(m -> users.findByUsername(m[0]).orElseThrow()).toList();
        Map<Long, Integer> held = new HashMap<>(); // copies currently held per book
        Map<Long, Set<Long>> holders = new HashMap<>(); // members holding each book, to avoid duplicates
        Map<Long, Integer> active = new HashMap<>(); // active loans per member (limit 3)
        List<Loan> toSave = new ArrayList<>();

        // aarav: one book out and overdue, one reservation waiting
        User aarav = members.get(0);
        Book clean = byTitle(catalogue, TITLES_ISSUED_TO_AARAV[0]);
        toSave.add(issued(clean, aarav, today.minusDays(LOAN_DAYS + 3), now));
        take(held, holders, active, clean, aarav);
        Book sapiens = byTitle(catalogue, TITLES_ISSUED_TO_AARAV[1]);
        Loan waiting = new Loan(sapiens, aarav);
        waiting.setReservedAt(now.minusDays(1).withHour(11));
        toSave.add(waiting);
        take(held, holders, active, sapiens, aarav);

        // every copy of one title is held
        Book scarce = byTitle(catalogue, "The Pragmatic Programmer");
        User holder = members.get(5);
        toSave.add(issued(scarce, holder, today.minusDays(4), now));
        take(held, holders, active, scarce, holder);

        // other members: books out (three of them late), reservations waiting
        int out = 0;
        int wait = 0;
        for (User member : members.subList(1, members.size())) {
            if (member == holder || active.getOrDefault(member.getId(), 0) > 0) {
                continue;
            }
            Book book = pickFree(catalogue, held, holders, member, random);
            if (book == null) {
                continue;
            }
            if (out < 11) {
                int daysAgo = out < 3 ? LOAN_DAYS + 2 + random.nextInt(9) : random.nextInt(LOAN_DAYS - 1);
                toSave.add(issued(book, member, today.minusDays(daysAgo), now));
                out++;
            } else if (wait < 6) {
                Loan l = new Loan(book, member);
                l.setReservedAt(now.minusDays(random.nextInt(3)).withHour(9 + random.nextInt(8)));
                toSave.add(l);
                wait++;
            } else {
                continue;
            }
            take(held, holders, active, book, member);
        }

        // history: returned books, a few late
        for (int i = 0; i < 24; i++) {
            User member = members.get(random.nextInt(members.size()));
            Book book = catalogue.get(random.nextInt(catalogue.size()));
            LocalDate issuedOn = today.minusDays(20 + random.nextInt(80));
            Loan l = issued(book, member, issuedOn, now);
            int lateDays = random.nextInt(4) == 0 ? 1 + random.nextInt(6) : 0;
            LocalDate returnedOn = issuedOn.plusDays(LOAN_DAYS - 1 - random.nextInt(8) + lateDays);
            l.setReturnedAt(LocalDateTime.of(returnedOn, LocalTime.of(10 + random.nextInt(6), random.nextInt(60))));
            l.giveBack(LIBRARIANS.get(random.nextInt(LIBRARIANS.size()))[0], lateDays * FINE_PER_DAY);
            l.setReturnedAt(LocalDateTime.of(returnedOn, LocalTime.of(10 + random.nextInt(6), random.nextInt(60))));
            toSave.add(l);
        }
        loans.saveAll(toSave);
    }

    private Loan issued(Book book, User member, LocalDate issuedOn, LocalDateTime now) {
        Loan loan = new Loan(book, member);
        loan.setReservedAt(issuedOn.minusDays(1).atTime(15, 30));
        loan.issue(LIBRARIANS.get(0)[0], issuedOn.plusDays(LOAN_DAYS));
        loan.setIssuedAt(issuedOn.atTime(10, 15));
        return loan;
    }

    private Book byTitle(List<Book> catalogue, String title) {
        return catalogue.stream().filter(b -> b.getTitle().equals(title)).findFirst().orElseThrow();
    }

    private void take(Map<Long, Integer> held, Map<Long, Set<Long>> holders, Map<Long, Integer> active, Book book, User member) {
        held.merge(book.getId(), 1, Integer::sum);
        holders.computeIfAbsent(book.getId(), k -> new HashSet<>()).add(member.getId());
        active.merge(member.getId(), 1, Integer::sum);
    }

    private Book pickFree(List<Book> catalogue, Map<Long, Integer> held, Map<Long, Set<Long>> holders, User member, Random random) {
        for (int attempt = 0; attempt < 30; attempt++) {
            Book book = catalogue.get(random.nextInt(catalogue.size()));
            boolean free = held.getOrDefault(book.getId(), 0) < book.getTotalCopies();
            // keep the demo "Pragmatic Programmer" fully held and leave aarav's reserved book to him
            if (free && !holders.getOrDefault(book.getId(), Set.of()).contains(member.getId()) && !book.getTitle().equals("The Pragmatic Programmer")) {
                return book;
            }
        }
        return null;
    }
}
