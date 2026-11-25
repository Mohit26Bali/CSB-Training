# Java I/O, NIO.2, Serialization and the Date-Time API: Detailed Notes

How a Java program reads and writes files and streams (classic `java.io` and modern `java.nio.file`), how objects are turned into bytes (serialization) and why that is dangerous, and how to work with dates, times and time zones using `java.time`.

> Examples target **Java 17 and 21**. All code was compiled and run. Imports are left out to save space: `java.io.*`, `java.nio.file.*`, `java.nio.charset.StandardCharsets`, `java.util.*`, `java.util.stream.*`, `java.time.*`, `java.time.format.*` and `java.time.temporal.*` cover everything. Paths are shown with `/`; on Windows the same code prints ``.

---

## Table of Contents

1. [The Big Picture: Bytes, Characters, Streams](#1-the-big-picture-bytes-characters-streams)
2. [Paths and the `Files` Class (NIO.2)](#2-paths-and-the-files-class-nio2)
3. [Reading and Writing Files with `Files`](#3-reading-and-writing-files-with-files)
4. [Classic `java.io`: Streams, Readers and Writers](#4-classic-javaio-streams-readers-and-writers)
5. [Character Encoding](#5-character-encoding)
6. [Directories: Listing, Walking, Searching](#6-directories-listing-walking-searching)
7. [Properties Files and Classpath Resources](#7-properties-files-and-classpath-resources)
8. [Serialization](#8-serialization)
9. [Why the Old Date API Was Replaced](#9-why-the-old-date-api-was-replaced)
10. [`java.time`: The Core Types](#10-javatime-the-core-types)
11. [Durations, Periods and Arithmetic](#11-durations-periods-and-arithmetic)
12. [Formatting and Parsing](#12-formatting-and-parsing)
13. [Time Zones and Daylight Saving](#13-time-zones-and-daylight-saving)
14. [Dates in Databases, JSON and Tests](#14-dates-in-databases-json-and-tests)
15. [Common Mistakes](#15-common-mistakes)
16. [Output Prediction Puzzles](#16-output-prediction-puzzles)
17. [Interview Questions](#17-interview-questions)
18. [Cheat Sheet](#18-cheat-sheet)
19. [Practice Tasks with Solutions](#19-practice-tasks-with-solutions)

---

## 1. The Big Picture: Bytes, Characters, Streams

Everything on disk or on a network is **bytes**. A **character** is text interpreted from bytes through an **encoding** (UTF-8, ISO-8859-1...).

| You want to handle | Use | Base types |
|---|---|---|
| **Bytes** (images, PDFs, any file) | byte streams | `InputStream`, `OutputStream` |
| **Text** (characters) | character streams | `Reader`, `Writer` (they apply an encoding) |
| Whole small files in one call | `Files.readString`, `Files.readAllBytes`, `Files.writeString` | |
| Large files, line by line | `Files.lines`, `BufferedReader` | |

A **stream** is a one-way flow of data that you read or write **in order**. The classic design is **decorating**: wrap a stream in another to add a feature.

```
FileInputStream -> BufferedInputStream -> ObjectInputStream
(raw bytes)        (adds a buffer)        (adds object decoding)
```

Two APIs coexist:

| | `java.io` (old) | `java.nio.file` (NIO.2, Java 7+) |
|---|---|---|
| File reference | `File` | `Path` |
| Operations | many on `File`, errors returned as `false` | `Files` static methods, errors thrown as **exceptions** with reasons |
| Symbolic links, attributes, walking | weak | good |
| Recommendation | needed for streams and readers | **prefer for all file operations** |

You can convert: `file.toPath()` and `path.toFile()`.

**Always close what you open.** Use **try-with-resources** (see the Exception notes):

```java
try (BufferedReader in = Files.newBufferedReader(path)) {
    // use in
}   // closed here, even if an exception was thrown
```

---

## 2. Paths and the `Files` Class (NIO.2)

A `Path` is an immutable name for a location; it does not need to exist.

```java
Path p = Path.of("data", "2026", "report.txt");        // data/2026/report.txt (separator added for the OS)
Path abs = p.toAbsolutePath();
Path q = Path.of("/home/asha/../asha/./notes.txt");
System.out.println(q.normalize());                      // /home/asha/notes.txt

p.getFileName();            // report.txt
p.getParent();              // data/2026
p.getRoot();                // null for a relative path
p.getNameCount();           // 3
p.resolve("backup");        // data/2026/report.txt/backup  (append)
p.resolveSibling("x.txt");  // data/2026/x.txt  (replace the last name)
Path.of("a/b").relativize(Path.of("a/b/c/d"));   // c/d
p.startsWith("data");       // true
```

- `Path.of(...)` is Java 11+; before that `Paths.get(...)`.
- On Windows the separator is `\`; Java accepts `/` as well. Never build paths by concatenating strings with a separator: use `Path.of` / `resolve`.
- **Path traversal:** when a path part comes from a user (a file upload name, a URL), `Path.of(base, userInput)` can escape with `../../etc/passwd`. Resolve, normalize, then check it still `startsWith(base)`:

```java
static Path safeResolve(Path base, String userName) {
    Path resolved = base.resolve(userName).normalize();
    if (!resolved.startsWith(base.normalize())) {
        throw new SecurityException("path escapes the base folder");
    }
    return resolved;
}
```

### Checking and querying

```java
Files.exists(p);  Files.notExists(p);
Files.isDirectory(p);  Files.isRegularFile(p);  Files.isReadable(p);
Files.size(p);                                   // bytes
Files.getLastModifiedTime(p);                    // FileTime
Files.isSameFile(a, b);
```

`exists` followed by an action is a **race** (the file can change in between). Prefer to try the action and handle `NoSuchFileException` / `FileAlreadyExistsException`.

---

## 3. Reading and Writing Files with `Files`

### Whole file in one call (small files only)

```java
Path file = Path.of("notes.txt");

Files.writeString(file, "line one\nline two\n");               // UTF-8, creates or overwrites
String text = Files.readString(file);                          // Java 11+
List<String> lines = Files.readAllLines(file);                 // ["line one", "line two"]
byte[] bytes = Files.readAllBytes(file);
Files.write(file, List.of("a", "b"));                          // lines
Files.write(file, bytes);                                      // bytes
```

These load the **entire file into memory**; do not use them for files of hundreds of megabytes.

### Open options

```java
Files.writeString(file, "more\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
```

| Option | Meaning |
|---|---|
| `CREATE` | create if missing (default with `write`) |
| `CREATE_NEW` | create, **fail if it exists** (safe against overwriting) |
| `APPEND` | add to the end |
| `TRUNCATE_EXISTING` | empty the file first (default with `write`) |
| `WRITE`, `READ` | access mode |

### Large files: stream lazily

```java
try (Stream<String> lines = Files.lines(file)) {              // Stream must be closed!
    long errors = lines.filter(l -> l.contains("ERROR")).count();
}

try (BufferedReader in = Files.newBufferedReader(file)) {
    String line;
    while ((line = in.readLine()) != null) {                  // null means end of file
        process(line);
    }
}

try (BufferedWriter out = Files.newBufferedWriter(file)) {
    out.write("hello");
    out.newLine();
}
```

`Files.lines` and `Files.list/walk` hold an open file handle: always put them in try-with-resources.

### Copy, move, delete

```java
Files.copy(src, dst);                                          // fails if dst exists
Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);          // rename; all or nothing on the same volume
Files.delete(p);                                               // throws if missing
Files.deleteIfExists(p);                                       // returns boolean
Files.createDirectories(Path.of("a/b/c"));                     // like mkdir -p
Files.createTempFile("report-", ".txt");
Files.createTempDirectory("work-");
```

A non-empty directory cannot be deleted directly; walk it and delete children first (section 6).

### Atomic writes (do not corrupt a file on a crash)

Write to a temporary file in the **same folder**, then move it over the target:

```java
Path tmp = Files.createTempFile(file.getParent(), "save-", ".tmp");
Files.writeString(tmp, newContent);
Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
```

Readers then see either the old file or the new one, never half of each.

---

## 4. Classic `java.io`: Streams, Readers and Writers

### Byte streams

```java
try (InputStream in = new FileInputStream("photo.jpg");
     OutputStream out = new FileOutputStream("copy.jpg")) {
    byte[] buffer = new byte[8192];
    int n;
    while ((n = in.read(buffer)) != -1) {          // -1 means end of stream
        out.write(buffer, 0, n);                   // write only the n bytes actually read
    }
}
// Java 9+: in.transferTo(out) does the same loop
```

A very common bug is `out.write(buffer)` after a short read, which writes junk from the previous round.

### Buffering

Reading or writing one byte at a time makes one operating-system call each. Wrap in `BufferedInputStream` / `BufferedOutputStream` / `BufferedReader` / `BufferedWriter` so data moves in large chunks. `BufferedReader` also gives `readLine()`.

### Character streams

```java
try (BufferedReader in = new BufferedReader(new FileReader("a.txt", StandardCharsets.UTF_8));
     PrintWriter out = new PrintWriter(new FileWriter("b.txt", StandardCharsets.UTF_8))) {
    String line;
    while ((line = in.readLine()) != null) out.println(line.toUpperCase());
}
```

| Class | Purpose |
|---|---|
| `InputStreamReader`, `OutputStreamWriter` | bridge: bytes to characters with a charset |
| `FileReader`, `FileWriter` | files as text (give the charset explicitly, Java 11+) |
| `BufferedReader`, `BufferedWriter` | buffering, `readLine`, `newLine` |
| `PrintWriter`, `PrintStream` | `println`, `printf`; **swallow exceptions** (check `checkError()`) |
| `StringReader`, `StringWriter`, `ByteArrayInputStream/OutputStream` | in-memory streams, handy for tests |
| `DataInputStream`, `DataOutputStream` | read/write primitives in a binary format |
| `ObjectInputStream`, `ObjectOutputStream` | serialization (section 8) |

### Reading standard input

```java
BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
```

or `Scanner` (see the Fundamentals notes).

### `File` essentials (still common)

```java
File f = new File("data/a.txt");
f.exists();  f.isFile();  f.isDirectory();  f.length();  f.getName();  f.getParent();
f.mkdirs();  f.delete();     // delete() returns false on failure, with no reason: another reason to prefer Files
f.listFiles();               // File[] or null if f is not a directory
```

---

## 5. Character Encoding

An encoding maps characters to bytes. The same bytes read with the wrong encoding give garbage (`MÃ¼ller` instead of `Müller`).

```java
byte[] bytes = "Müller €".getBytes(StandardCharsets.UTF_8);     // 11 bytes: ü takes 2 and € takes 3
String back = new String(bytes, StandardCharsets.UTF_8);
```

| Encoding | Bytes per character |
|---|---|
| ASCII | 1 (only 128 characters) |
| ISO-8859-1 (Latin-1) | 1 (256 characters; no `€`) |
| **UTF-8** | **1 to 4** (ASCII-compatible; the web standard) |
| UTF-16 | 2 or 4 (what Java uses internally for `char`) |

- **Since Java 18 the default charset is UTF-8** on every platform (JEP 400). Older Java used the operating system's setting (Windows-1252 on many Windows machines), which made files unreadable between machines.
- Still **always pass the charset explicitly** (`StandardCharsets.UTF_8`): it documents intent and works on older Java.
- `Files.readString` and `Files.newBufferedReader` default to UTF-8 and **throw `MalformedInputException`** on invalid bytes (a file that is really Latin-1). The old `FileReader` silently replaced bad bytes.
- HTTP: set `Content-Type: application/json; charset=UTF-8`; databases: use `utf8mb4` in MySQL.
- `String.length()` counts UTF-16 `char`s, so an emoji (U+1F600) has length 2; use `codePointCount` for real characters.

---

## 6. Directories: Listing, Walking, Searching

```java
// direct children only
try (Stream<Path> children = Files.list(dir)) {
    children.filter(Files::isRegularFile).forEach(System.out::println);
}

// the whole tree, depth-first (parent before children)
try (Stream<Path> tree = Files.walk(dir)) {
    long javaFiles = tree.filter(p -> p.toString().endsWith(".java")).count();
}

// search with a predicate and a depth limit
try (Stream<Path> found = Files.find(dir, 3, (p, attrs) -> attrs.isRegularFile() && attrs.size() > 1_000_000)) { }

// glob filter
try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.{txt,md}")) { }
```

### Deleting a folder tree

```java
try (Stream<Path> tree = Files.walk(dir)) {
    tree.sorted(Comparator.reverseOrder())        // children before parents
        .forEach(p -> {
            try { Files.delete(p); } catch (IOException e) { throw new UncheckedIOException(e); }
        });
}
```

For a tree you do not need to stream, `Files.walkFileTree(dir, new SimpleFileVisitor<>() { ... })` gives callbacks for each file and directory.

### Watching for changes

`WatchService` reports create/modify/delete events in a folder (used by hot-reload tools); events can be delayed or duplicated, so treat them as hints.

---

## 7. Properties Files and Classpath Resources

### `Properties`

```java
Properties props = new Properties();
try (InputStream in = Files.newInputStream(Path.of("app.properties"))) {
    props.load(in);                                      // key=value lines
}
String url = props.getProperty("db.url");                // null if missing
int timeout = Integer.parseInt(props.getProperty("timeout", "30"));   // with a default

props.setProperty("theme", "dark");
try (OutputStream out = Files.newOutputStream(Path.of("app.properties"))) {
    props.store(out, "settings");
}
```

`Properties` extends `Hashtable<Object,Object>`: values are strings. Spring Boot's `application.properties` / `application.yml` are the framework equivalent.

### Files packaged inside the jar (the classpath)

Files under `src/main/resources` are not on the file system after packaging, so `new File("config.json")` fails. Read them from the class loader:

```java
try (InputStream in = MyApp.class.getResourceAsStream("/config.json")) {   // leading / = from the classpath root
    if (in == null) throw new FileNotFoundException("config.json not on the classpath");
    String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
}
```

In Spring: `new ClassPathResource("config.json").getInputStream()` or `@Value("classpath:config.json") Resource`.

---

## 8. Serialization

**Serialization** turns an object graph into bytes (to save it or send it); **deserialization** rebuilds it.

```java
class User implements Serializable {
    private static final long serialVersionUID = 1L;
    String name;
    int age;
    transient String password;            // never serialized: comes back as null
    User(String name, int age, String password) { this.name = name; this.age = age; this.password = password; }
}

try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(file))) {
    out.writeObject(new User("Asha", 30, "secret"));
}
try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(file))) {
    User u = (User) in.readObject();       // u.password is null
}
```

| Concept | Meaning |
|---|---|
| `Serializable` | a **marker interface** (no methods): opts the class in |
| `serialVersionUID` | version stamp. If it differs between the writer's and reader's class, deserialization fails with `InvalidClassException`. Declare it explicitly. |
| `transient` | field is skipped (and `static` fields are never serialized) |
| Fields of other objects | those classes must be `Serializable` too, or you get `NotSerializableException` |
| Parent class | if the parent is **not** `Serializable`, its fields are not saved and its **no-arg constructor** runs on deserialization |
| Constructors | **not called** for the serializable classes themselves: objects are rebuilt without running constructors, so invariants can be bypassed |
| Custom control | `private void writeObject / readObject`, `readResolve`, `Externalizable` |
| Records | serialized by their components using the canonical constructor (safer than classes) |
| Enums | serialized by name; the same constant instance is preserved |

### Why Java serialization is dangerous

Deserializing **untrusted bytes** can run attacker-chosen code: crafted objects abuse classes already on the classpath ("gadget chains") during `readObject`. This is the root of many severe vulnerabilities (insecure deserialization is on the OWASP Top 10).

- **Never** deserialize data from the network, a cookie, a cache or a file a user can write.
- If you must, use an **`ObjectInputFilter`** (Java 9+; `-Djdk.serialFilter=...`) with an allow-list of classes.
- It is also **brittle** (any class change can break old data), slow, and tied to Java.

### What to use instead

| Need | Use |
|---|---|
| Data exchange between services or storage | **JSON** (Jackson), Protocol Buffers, Avro |
| Copying an object | a copy constructor or a mapper, not serialize/deserialize |
| Caching objects (Redis) | JSON or a binary format with a schema |
| Session data | keep it small, avoid storing live objects |

The Java Serialization classes still appear in older frameworks and interviews, so know the vocabulary above.

---

## 9. Why the Old Date API Was Replaced

`java.util.Date`, `Calendar`, and `SimpleDateFormat` (Java 1.0 and 1.1) have well-known flaws:

| Problem | Example |
|---|---|
| **Mutable** | `date.setTime(...)` changes a shared object; not thread-safe |
| **Confusing** | `new Date(2026, 1, 5)` means year 3926, month February... (year offset 1900, months from 0) |
| `Date` is really an **instant**, not a date | it prints a time zone it does not store |
| `SimpleDateFormat` is **not thread-safe** | a static shared formatter corrupts output under load |
| No types for "date only", "time only" or "duration" | |
| Poor time-zone and DST handling | |

**`java.time`** (Java 8, from Joda-Time) fixes all of these: **immutable** (every operation returns a new object, so thread-safe), clear types, months numbered 1 to 12, and `DateTimeFormatter` is thread-safe.

Conversions to the legacy world: `date.toInstant()`, `Date.from(instant)`, `java.sql.Date.valueOf(localDate)`, `timestamp.toLocalDateTime()`.

---

## 10. `java.time`: The Core Types

| Type | Holds | Example | Use for |
|---|---|---|---|
| `LocalDate` | date, no time, no zone | `2026-11-25` | birthdays, due dates |
| `LocalTime` | time, no date, no zone | `14:30:00` | opening hours |
| `LocalDateTime` | date and time, **no zone** | `2026-11-25T14:30` | a wall-clock reading (a meeting "at 3 pm" in some place) |
| `ZonedDateTime` | date, time and zone | `2026-11-25T14:30+05:30[Asia/Kolkata]` | a real moment seen in a zone, with DST rules |
| `OffsetDateTime` | date, time and fixed offset | `2026-11-25T14:30+05:30` | what databases and APIs send |
| `Instant` | a point on the global timeline (UTC) | `2026-11-25T09:00:00Z` | timestamps, logs, "when did it happen" |
| `Duration` | amount of time in seconds/nanos | `PT2H30M` | elapsed time |
| `Period` | amount in years/months/days | `P1Y2M3D` | calendar differences |
| `Year`, `YearMonth`, `MonthDay`, `DayOfWeek`, `Month` | parts | | |
| `ZoneId`, `ZoneOffset` | `Asia/Kolkata`, `+05:30` | | |

```java
LocalDate date = LocalDate.of(2026, 11, 25);          // months are 1 to 12 (or Month.NOVEMBER)
LocalDate today = LocalDate.now();                    // uses the system clock and zone
LocalTime t = LocalTime.of(14, 30);
LocalDateTime dt = LocalDateTime.of(date, t);
ZonedDateTime ist = dt.atZone(ZoneId.of("Asia/Kolkata"));
Instant moment = ist.toInstant();                     // 2026-11-25T09:00:00Z

date.getDayOfWeek();         // WEDNESDAY
date.getMonth();             // NOVEMBER
date.getDayOfYear();         // 329
date.isLeapYear();           // false
date.lengthOfMonth();        // 30
```

**Choosing a type:**

- A **moment that happened** (created at, logged at): `Instant`.
- A **calendar date** with no clock (birthday, holiday): `LocalDate`.
- A **future appointment** at a local time in a place: `LocalDateTime` + a zone (`ZonedDateTime`), because the UTC time of "9 am in London" changes with daylight saving.
- Never use `LocalDateTime` for something that must be the same moment for everybody.

All the types are **immutable**; ignoring the result of `plusDays(1)` does nothing.

---

## 11. Durations, Periods and Arithmetic

### Adding and subtracting

```java
LocalDate d = LocalDate.of(2026, 1, 31);
d.plusDays(10);               // 2026-02-10
d.plusMonths(1);              // 2026-02-28   (clamped to the last valid day, not an error)
d.minusYears(1);              // 2025-01-31
d.withDayOfMonth(1);          // 2026-01-01
d.plus(2, ChronoUnit.WEEKS);  // 2026-02-14
```

### `TemporalAdjusters`

```java
d.with(TemporalAdjusters.firstDayOfNextMonth());          // 2026-02-01
d.with(TemporalAdjusters.next(DayOfWeek.MONDAY));         // next Monday
d.with(TemporalAdjusters.lastDayOfMonth());
```

### `Duration` (time-based) vs `Period` (date-based)

```java
Duration dur = Duration.between(LocalTime.of(9, 0), LocalTime.of(17, 30));
dur.toHours();                // 8
dur.toMinutes();              // 510
dur.toMinutesPart();          // 30  (the minutes part after whole hours)

Period per = Period.between(LocalDate.of(2000, 3, 15), LocalDate.of(2026, 11, 25));
per.getYears();               // 26
per.getMonths();              // 8
per.getDays();                // 10

ChronoUnit.DAYS.between(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 25));   // 358
```

| | `Duration` | `Period` |
|---|---|---|
| Unit | seconds and nanoseconds | years, months, days |
| Works with | `Instant`, `LocalTime`, `LocalDateTime`, `ZonedDateTime` | `LocalDate` |
| "One day" is | exactly 24 hours | a calendar day (23 or 25 hours on a DST change) |

Compare: `isBefore`, `isAfter`, `isEqual`, `compareTo`. For a person's age: `Period.between(birth, today).getYears()`.

---

## 12. Formatting and Parsing

```java
LocalDateTime dt = LocalDateTime.of(2026, 11, 25, 14, 5, 9);

DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
dt.format(f);                                             // 25 Nov 2026, 14:05
LocalDateTime parsed = LocalDateTime.parse("25 Nov 2026, 14:05", f);

dt.toString();                                            // 2026-11-25T14:05:09  (ISO-8601)
LocalDate.parse("2026-11-25");                            // ISO format needs no formatter
DateTimeFormatter.ISO_LOCAL_DATE.format(dt);              // 2026-11-25
DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.UK);
```

| Letter | Meaning | Example |
|---|---|---|
| `yyyy` | **year** | 2026 |
| `YYYY` | **week-based year** (a famous bug: `31 Dec 2024` formats as `2025`) | avoid |
| `MM`, `MMM`, `MMMM` | month: 11, Nov, November | |
| `dd` | day of month | 05 |
| `HH` | hour 00-23 | 14 |
| `hh` + `a` | hour 01-12 + AM/PM | 02 PM |
| `mm`, `ss`, `SSS` | minute, second, millisecond | |
| `EEE`, `EEEE` | Mon, Monday | |
| `z`, `VV`, `XXX` | zone name, zone id, offset | IST, Asia/Kolkata, +05:30 |

- `mm` is minutes and `MM` is months: mixing them up compiles and gives wrong dates.
- Parsing is **strict by default** for invalid values (`2026-02-30` throws `DateTimeParseException`) and the pattern must match exactly.
- `DateTimeFormatter` is **immutable and thread-safe**: make it a `static final` constant.
- Month and day **names depend on the locale**; pass `Locale.ENGLISH` for stable output.

---

## 13. Time Zones and Daylight Saving

- A **zone id** like `Asia/Kolkata` or `America/New_York` carries the rules, including daylight saving history. An **offset** like `+05:30` is only a number.
- Use **region ids**, not abbreviations: `IST` can mean India, Israel or Ireland.

```java
ZoneId kolkata = ZoneId.of("Asia/Kolkata");
ZoneId newYork = ZoneId.of("America/New_York");

ZonedDateTime meeting = ZonedDateTime.of(2026, 11, 25, 15, 0, 0, 0, kolkata);
ZonedDateTime inNy = meeting.withZoneSameInstant(newYork);       // 2026-11-25T04:30-05:00[America/New_York]
ZonedDateTime sameClock = meeting.withZoneSameLocal(newYork);    // same wall clock, a different moment!
```

### Daylight saving traps

On the spring-forward day, a local time does not exist; on the fall-back day, it happens twice.

```java
ZoneId ny = ZoneId.of("America/New_York");
LocalDateTime gap = LocalDateTime.of(2026, 3, 8, 2, 30);          // clocks jump from 02:00 to 03:00
System.out.println(ZonedDateTime.of(gap, ny));                    // 2026-03-08T03:30-04:00[America/New_York]  (moved forward)

ZonedDateTime before = ZonedDateTime.of(2026, 3, 7, 12, 0, 0, 0, ny);
System.out.println(before.plusDays(1));                           // same clock time next day: 2026-03-08T12:00-04:00
System.out.println(before.plus(Duration.ofHours(24)));            // exactly 24 hours later: 2026-03-08T13:00-04:00
```

Adding **a day** keeps the clock time; adding **24 hours** moves the exact amount of time. Pick the one that matches the meaning ("tomorrow at noon" vs "in 24 hours").

---

## 14. Dates in Databases, JSON and Tests

### Rules of thumb

| Situation | Store / send |
|---|---|
| When something happened | `Instant` / `timestamp with time zone`, or UTC `datetime`; convert to the user's zone only for display |
| A calendar date (birth date) | `LocalDate` / `DATE` |
| A future event in a place | local date-time **plus** the zone id |
| API contracts | ISO-8601 strings: `2026-11-25T09:00:00Z` |

**JPA / Hibernate 6:** `LocalDate`, `LocalDateTime`, `Instant`, `OffsetDateTime` map directly. **JDBC 4.2:** `rs.getObject("created", LocalDateTime.class)`, `ps.setObject(1, instant)`; avoid `java.sql.Timestamp` in new code.

**Jackson:** include the `jackson-datatype-jsr310` module (Spring Boot does) and keep `WRITE_DATES_AS_TIMESTAMPS` off to emit ISO strings. (Spring Boot 4 uses Jackson 3, where `java.time` support is built in.)

**Server time zone:** set servers and databases to **UTC**, so that behaviour does not change when you deploy in another region.

### Testing time-dependent code with a `Clock`

`LocalDate.now()` reads the real clock, which makes tests flaky. Inject a `Clock`:

```java
class Subscription {
    private final Clock clock;
    Subscription(Clock clock) { this.clock = clock; }
    boolean expired(LocalDate expiry) { return LocalDate.now(clock).isAfter(expiry); }
}

Clock fixed = Clock.fixed(Instant.parse("2026-11-25T00:00:00Z"), ZoneOffset.UTC);
new Subscription(fixed).expired(LocalDate.of(2026, 11, 24));      // true, always
```

In production use `Clock.systemUTC()` (or `Clock.systemDefaultZone()`).

---

## 15. Common Mistakes

| Mistake | Why it is wrong | Fix |
|---|---|---|
| Not closing a stream, `Files.lines` or `Files.list` | leaked file handles (and locked files on Windows) | try-with-resources |
| `out.write(buffer)` after a short read | writes stale bytes | `write(buffer, 0, n)` |
| Reading a huge file with `readAllLines` | out of memory | `Files.lines` or `BufferedReader` |
| Relying on the default charset | differs across machines before Java 18 | always `StandardCharsets.UTF_8` |
| `new File("config.json")` for a packaged resource | not on disk inside a jar | `getResourceAsStream` |
| Building a path from user input | `../../` escapes the folder | resolve, normalize, check `startsWith` |
| Check `exists()` then act | race between check and act | act and handle the exception |
| Deserializing untrusted bytes | remote code execution | do not; use JSON or an allow-list filter |
| Missing `serialVersionUID` | any change can break compatibility unpredictably | declare it |
| `new Date(2026, 11, 25)` | year offset and zero-based months | `LocalDate.of(2026, 11, 25)` |
| `YYYY` in a pattern | week-based year, wrong around New Year | `yyyy` |
| `mm` for month | it is minutes | `MM` |
| Shared `SimpleDateFormat` | not thread-safe | `DateTimeFormatter` constant |
| Using `LocalDateTime` for global timestamps | no zone, ambiguous | `Instant` or `OffsetDateTime` |
| Ignoring the result of `plusDays` | immutable | `d = d.plusDays(1)` |
| Adding `Duration.ofDays(1)` and expecting a calendar day | DST days are 23 or 25 hours | `plusDays(1)` on a `ZonedDateTime` |
| Using zone abbreviations (`IST`, `EST`) | ambiguous | region ids (`Asia/Kolkata`) |
| `LocalDate.now()` in logic under test | flaky tests | inject a `Clock` |

---

## 16. Output Prediction Puzzles

**Puzzle 1: month clamping**

```java
LocalDate d = LocalDate.of(2026, 1, 31);
System.out.println(d.plusMonths(1));
System.out.println(d.plusMonths(1).plusMonths(1));
System.out.println(d.plusMonths(2));
```

**Puzzle 2: immutability**

```java
LocalDate d = LocalDate.of(2026, 5, 10);
d.plusDays(5);
System.out.println(d);
```

**Puzzle 3: `Period` vs `ChronoUnit`**

```java
LocalDate a = LocalDate.of(2026, 1, 15);
LocalDate b = LocalDate.of(2026, 3, 10);
System.out.println(Period.between(a, b));
System.out.println(ChronoUnit.DAYS.between(a, b));
System.out.println(ChronoUnit.MONTHS.between(a, b));
```

**Puzzle 4: `Duration` parts**

```java
Duration d = Duration.ofMinutes(135);
System.out.println(d);
System.out.println(d.toHours() + " " + d.toMinutesPart());
```

**Puzzle 5: the week-based year**

```java
LocalDate d = LocalDate.of(2024, 12, 30);
System.out.println(d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
System.out.println(d.format(DateTimeFormatter.ofPattern("YYYY-MM-dd", Locale.US)));
```

**Puzzle 6: same instant, different zones**

```java
ZonedDateTime kolkata = ZonedDateTime.of(2026, 11, 25, 15, 0, 0, 0, ZoneId.of("Asia/Kolkata"));
System.out.println(kolkata.withZoneSameInstant(ZoneId.of("UTC")));
System.out.println(kolkata.withZoneSameLocal(ZoneId.of("UTC")));
System.out.println(kolkata.toInstant());
```

**Puzzle 7: daylight saving**

```java
ZoneId ny = ZoneId.of("America/New_York");
ZonedDateTime t = ZonedDateTime.of(2026, 3, 7, 12, 0, 0, 0, ny);
System.out.println(t.plusDays(1).toLocalTime());
System.out.println(t.plus(Duration.ofHours(24)).toLocalTime());
```

**Puzzle 8: invalid date**

```java
try {
    LocalDate.of(2026, 2, 30);
} catch (DateTimeException e) {
    System.out.println("invalid");
}
System.out.println(LocalDate.of(2024, 2, 29).plusYears(1));
```

**Puzzle 9: parsing**

```java
DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy");
System.out.println(LocalDate.parse("05/11/2026", f));
try {
    LocalDate.parse("2026-11-05", f);
} catch (DateTimeParseException e) {
    System.out.println("bad format");
}
```

**Puzzle 10: path resolution**

```java
Path base = Path.of("/data/app");
System.out.println(base.resolve("logs/today.txt"));
System.out.println(base.resolve("/etc/passwd"));
System.out.println(base.resolve("../secret").normalize());
System.out.println(Path.of("/a/b").relativize(Path.of("/a/b/c/d.txt")));
```

**Puzzle 11: files and charsets (run in a temp folder)**

```java
Path dir = Files.createTempDirectory("puzzle");
Path file = dir.resolve("a.txt");
Files.writeString(file, "Müller");
System.out.println(Files.size(file));
System.out.println(Files.readString(file).length());
Files.writeString(file, "x", StandardOpenOption.APPEND);
System.out.println(Files.readString(file));
try {
    Files.writeString(file, "y", StandardOpenOption.CREATE_NEW);
} catch (FileAlreadyExistsException e) {
    System.out.println("exists");
}
```

**Puzzle 12: `transient`**

```java
static class User implements Serializable {
    String name = "Asha";
    transient String password = "secret";
}
ByteArrayOutputStream bytes = new ByteArrayOutputStream();
try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
    out.writeObject(new User());
}
try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
    User u = (User) in.readObject();
    System.out.println(u.name + " " + u.password);
}
```

**Answers**

1. **`2026-02-28`**, **`2026-03-28`**, **`2026-03-31`**. `plusMonths` clamps to the last valid day, so Jan 31 plus one month is Feb 28. Adding one month twice is not the same as adding two months at once (28 Mar vs 31 Mar).
2. **`2026-05-10`**. `plusDays` returns a new object that was thrown away.
3. **`P1M23D`**, **`54`**, **`1`**. The period is one month and 23 days (15 Jan to 15 Feb, then to 10 Mar is 23 days in a 28-day February); the total days is 54; complete months between is 1.
4. **`PT2H15M`**, then **`2 15`**. `toHours` is the whole hours; `toMinutesPart` is the remaining minutes.
5. **`2024-12-30`**, **`2025-12-30`**. `YYYY` is the week-based year: 30 Dec 2024 belongs to week 1 of 2025 (in the US locale). Use `yyyy`.
6. **`2026-11-25T09:30Z[UTC]`**, **`2026-11-25T15:00Z[UTC]`**, **`2026-11-25T09:30:00Z`**. `withZoneSameInstant` keeps the moment (15:00 IST is 09:30 UTC); `withZoneSameLocal` keeps the clock reading, which is a different moment.
7. **`12:00`**, **`13:00`**. 8 March 2026 is the spring-forward day in New York: one day later keeps the clock at noon, but exactly 24 hours later is 1 pm.
8. **`invalid`**, then **`2025-02-28`**. February 30 does not exist. Adding a year to 29 Feb 2024 clamps to 28 Feb 2025.
9. **`2026-11-05`**, then **`bad format`**. The pattern must match the text exactly.
10. **`/data/app/logs/today.txt`**, **`/etc/passwd`**, **`/data/secret`**, **`c/d.txt`**. Resolving an **absolute** path discards the base (this is why user input must be checked), and `..` is only removed by `normalize`.
11. **`7`**, **`6`**, **`Müllerx`**, **`exists`**. `ü` takes two bytes in UTF-8, so 6 characters are 7 bytes; `CREATE_NEW` fails when the file exists.
12. **`Asha null`**. The `transient` field is not serialized and comes back as the default `null`.

---

## 17. Interview Questions

**1. What is the difference between `InputStream` and `Reader`?**
`InputStream` reads **bytes**; `Reader` reads **characters**, decoding bytes with a charset. Use streams for binary data and readers for text.

**2. What is the decorator pattern in Java I/O?**
Wrapping one stream in another to add behaviour, e.g. `new BufferedReader(new InputStreamReader(new FileInputStream(f)))`.

**3. Why use buffered streams?**
They reduce the number of expensive system calls by reading and writing in large chunks.

**4. `File` vs `Path`?**
`Path` (NIO.2) is the modern API with real exceptions, symbolic-link and attribute support, and the `Files` helper methods. `File` is the legacy API whose methods often just return `false` on failure.

**5. How do you read a very large file?**
Line by line with `Files.lines` or `BufferedReader`, inside try-with-resources, never `readAllLines` or `readAllBytes`.

**6. What is `serialVersionUID`?**
A version number for a serializable class; if it differs between sender and receiver, deserialization throws `InvalidClassException`. Declare it explicitly.

**7. What does `transient` do?**
Marks a field to be skipped by serialization; it is restored as the default value.

**8. Why is Java deserialization a security risk?**
Deserializing attacker-controlled bytes can instantiate gadget classes and execute code. Do not deserialize untrusted data; use JSON, or an `ObjectInputFilter` allow-list.

**9. Is the constructor called when deserializing?**
Not for the serializable class itself. The no-arg constructor of the first non-serializable superclass is called.

**10. What are the problems with `java.util.Date` and `SimpleDateFormat`?**
Mutable, not thread-safe, year offset 1900 and zero-based months, no separate date/time/duration types, and weak time-zone support.

**11. `LocalDateTime` vs `ZonedDateTime` vs `Instant`?**
`LocalDateTime` is a wall-clock reading with no zone; `ZonedDateTime` is a wall-clock reading in a zone with DST rules; `Instant` is a point on the UTC timeline.

**12. `Duration` vs `Period`?**
`Duration` is time-based (seconds, nanos) for instants and times; `Period` is date-based (years, months, days) for `LocalDate`.

**13. Is `DateTimeFormatter` thread-safe?**
Yes, it is immutable (unlike `SimpleDateFormat`), so it can be a `static final` constant.

**14. What time zone should a server and database use, and what should you store?**
UTC for servers and databases; store instants (UTC) for when things happened and convert to the user's zone only for display. Store a zone id with future local appointments.

**15. How do you test code that depends on the current time?**
Inject a `java.time.Clock` and use `Clock.fixed(...)` in tests.

**16. What changed about the default charset in Java 18?**
UTF-8 became the default on all platforms (JEP 400). Passing the charset explicitly is still best practice.

---

## 18. Cheat Sheet

| Topic | Remember |
|---|---|
| Bytes / text | `InputStream`/`OutputStream` / `Reader`/`Writer` |
| Files (modern) | `Path.of(...)`, `Files.readString`, `writeString`, `lines`, `copy`, `move`, `delete` |
| Close it | try-with-resources; `Files.lines`, `list`, `walk` return closeable streams |
| Big files | stream; never read the whole file |
| Text encoding | always `StandardCharsets.UTF_8` (default only since Java 18) |
| User paths | `resolve`, `normalize`, check `startsWith(base)` |
| Resources in a jar | `getResourceAsStream("/name")`, not `File` |
| Serialization | `Serializable`, `serialVersionUID`, `transient`; **never** deserialize untrusted data |
| Dates | `java.time`, immutable, months 1-12 |
| Types | `Instant` (moment), `LocalDate` (date), `LocalDateTime` (no zone), `ZonedDateTime` (zone + DST) |
| Amounts | `Duration` (time), `Period` (calendar) |
| Formatting | `yyyy` not `YYYY`; `MM` month, `mm` minute; formatter is thread-safe |
| Zones | region ids (`Asia/Kolkata`); servers and DB in UTC |
| Testing | inject `Clock` |

---

## 19. Practice Tasks with Solutions

**Task 1.** Count the words in a text file, streaming it.

```java
static long countWords(Path file) throws IOException {
    try (Stream<String> lines = Files.lines(file)) {
        return lines.flatMap(l -> Arrays.stream(l.trim().split("\\s+")))
                    .filter(w -> !w.isEmpty())
                    .count();
    }
}
```

**Task 2.** Copy a file safely, refusing to overwrite and creating the destination folder.

```java
static void safeCopy(Path src, Path dst) throws IOException {
    Files.createDirectories(dst.toAbsolutePath().getParent());
    Files.copy(src, dst);             // throws FileAlreadyExistsException if dst exists
}
```

**Task 3.** Find the five largest files under a folder.

```java
static List<Path> largest(Path root) throws IOException {
    try (Stream<Path> tree = Files.walk(root)) {
        return tree.filter(Files::isRegularFile)
                   .sorted(Comparator.comparingLong((Path p) -> {
                       try { return Files.size(p); } catch (IOException e) { return 0L; }
                   }).reversed())
                   .limit(5)
                   .toList();
    }
}
```

**Task 4.** Append a timestamped line to a log file in UTC.

```java
static void log(Path file, String message) throws IOException {
    String line = Instant.now() + " " + message + System.lineSeparator();
    Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
}
```

**Task 5.** Compute a person's age in years from a birth date, and the days until their next birthday.

```java
static int age(LocalDate birth, LocalDate today) {
    return Period.between(birth, today).getYears();
}
static long daysToNextBirthday(LocalDate birth, LocalDate today) {
    LocalDate next = birth.withYear(today.getYear());
    if (!next.isAfter(today)) next = next.plusYears(1);
    return ChronoUnit.DAYS.between(today, next);
}
// age(2000-03-15, 2026-11-25) is 26; daysToNextBirthday is 110
```

**Task 6.** Convert a meeting time from Kolkata to New York and London.

```java
ZonedDateTime meeting = ZonedDateTime.of(2026, 11, 25, 15, 0, 0, 0, ZoneId.of("Asia/Kolkata"));
for (String zone : List.of("America/New_York", "Europe/London")) {
    System.out.println(zone + " " + meeting.withZoneSameInstant(ZoneId.of(zone)).toLocalDateTime());
}
// America/New_York 2026-11-25T04:30
// Europe/London 2026-11-25T09:30
```

**Task 7.** Count the working days (Monday to Friday) between two dates, inclusive.

```java
static long workingDays(LocalDate from, LocalDate to) {
    return from.datesUntil(to.plusDays(1))
               .filter(d -> d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY)
               .count();
}
// workingDays(2026-11-23, 2026-11-29) is 5
```

**Task 8.** Write a small `Config` class that loads `app.properties` from the classpath with defaults.

```java
class Config {
    private final Properties props = new Properties();
    Config() throws IOException {
        try (InputStream in = Config.class.getResourceAsStream("/app.properties")) {
            if (in != null) props.load(in);              // missing file: use defaults only
        }
    }
    String host() { return props.getProperty("host", "localhost"); }
    int port() { return Integer.parseInt(props.getProperty("port", "8080")); }
}
```

*End of notes.*
