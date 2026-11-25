# Java Generics, Enums and Nested Classes: Detailed Notes

Three language features that appear in almost every real codebase and in a lot of interviews: **generics** (type-safe reusable code, wildcards, type erasure), **enums** (a fixed set of constants that can carry data and behaviour), and **nested classes** (static nested, inner, local and anonymous classes). Each section explains the idea, why it exists, how it works underneath, and the traps.

> Examples target **Java 17 and 21**. All code was compiled and run.

---

## Table of Contents

1. [Why Generics](#1-why-generics)
2. [Generic Classes, Interfaces and Methods](#2-generic-classes-interfaces-and-methods)
3. [Bounded Type Parameters](#3-bounded-type-parameters)
4. [Wildcards and PECS](#4-wildcards-and-pecs)
5. [Type Erasure and Its Consequences](#5-type-erasure-and-its-consequences)
6. [Generics Rules and Restrictions](#6-generics-rules-and-restrictions)
7. [Enums: The Basics](#7-enums-the-basics)
8. [Enums with Fields, Constructors and Methods](#8-enums-with-fields-constructors-and-methods)
9. [Enums with Behaviour, Interfaces and the Collections](#9-enums-with-behaviour-interfaces-and-the-collections)
10. [Nested Classes: The Four Kinds](#10-nested-classes-the-four-kinds)
11. [Inner Classes in Depth](#11-inner-classes-in-depth)
12. [Local and Anonymous Classes, and How Lambdas Differ](#12-local-and-anonymous-classes-and-how-lambdas-differ)
13. [Common Mistakes](#13-common-mistakes)
14. [Output Prediction Puzzles](#14-output-prediction-puzzles)
15. [Interview Questions](#15-interview-questions)
16. [Cheat Sheet](#16-cheat-sheet)
17. [Practice Tasks with Solutions](#17-practice-tasks-with-solutions)

---

## 1. Why Generics

Before generics (Java 5), collections held `Object`. You had to cast on the way out, and a wrong cast failed only at **run time**:

```java
List list = new ArrayList();          // raw type: a list of Object
list.add("hello");
list.add(42);                         // the compiler does not object
String s = (String) list.get(1);      // ClassCastException at run time
```

With generics the element type is part of the type, and the compiler checks it:

```java
List<String> names = new ArrayList<>();
names.add("hello");
// names.add(42);                     // compile error: int cannot be converted to String
String s = names.get(0);              // no cast
```

| Benefit | Meaning |
|---|---|
| **Type safety** | wrong types are caught at compile time, not in production |
| **No casts** | cleaner code |
| **Reuse** | one `Box<T>` works for any type, instead of `StringBox`, `IntBox`... |
| **Self-documenting** | `Map<String, List<Order>>` says what it holds |

**Analogy:** a labelled storage box. `Box<Book>` can only be filled with books and you know a book comes out. An unlabelled box (raw type) can hold anything and you have to check every time.

---

## 2. Generic Classes, Interfaces and Methods

### A generic class

```java
public class Box<T> {
    private T value;
    public Box(T value) { this.value = value; }
    public T get() { return value; }
    public void set(T value) { this.value = value; }
}

Box<String> a = new Box<>("hi");        // diamond <>: the compiler infers String
Box<Integer> b = new Box<>(42);
String s = a.get();
```

`T` is a **type parameter**. Conventional names: `T` (type), `E` (element), `K` (key), `V` (value), `N` (number), `R` (result). A type argument must be a **reference type**: `Box<int>` is illegal, `Box<Integer>` works (autoboxing).

### Several type parameters

```java
public class Pair<K, V> {
    private final K key;
    private final V value;
    public Pair(K key, V value) { this.key = key; this.value = value; }
    public K getKey() { return key; }
    public V getValue() { return value; }
    @Override public String toString() { return key + "=" + value; }
}

Pair<String, Integer> p = new Pair<>("age", 30);
```

Since Java 16 a `record Pair<K, V>(K key, V value) {}` does the same in one line.

### A generic interface

```java
interface Repository<T, ID> {
    void save(T item);
    T findById(ID id);
}

class UserRepository implements Repository<User, Long> {      // fix the types
    public void save(User u) { }
    public User findById(Long id) { return null; }
}
```

Spring Data's `JpaRepository<User, Long>` is exactly this pattern.

### Generic methods

A method can have its own type parameter, written **before the return type**, even inside a non-generic class.

```java
static <T> T firstOrDefault(List<T> list, T fallback) {
    return list.isEmpty() ? fallback : list.get(0);
}

String s = firstOrDefault(List.of("a", "b"), "none");     // T inferred as String
int n    = firstOrDefault(List.of(), 0);                  // T inferred as Integer
String t = Util.<String>firstOrDefault(List.of(), "x");   // explicit type witness (rarely needed)
```

```java
static <T> void swap(T[] array, int i, int j) {
    T tmp = array[i]; array[i] = array[j]; array[j] = tmp;
}
```

### Generic constructors and static members

- Generic **static methods** need their own `<T>`; a class's `T` is not available in static context (there is no instance to give it a type).
- A static **field** cannot have type `T`.

---

## 3. Bounded Type Parameters

An unbounded `T` is just `Object`: you can only call `Object`'s methods. A **bound** restricts `T` and lets you use the bound's methods.

```java
static <T extends Comparable<T>> T max(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;       // compareTo is available because of the bound
}

max(3, 7);            // 7
max("pear", "apple"); // pear
// max(new Object(), new Object());   // compile error: Object is not Comparable
```

- `extends` here means "is a subtype of", for both classes and interfaces.
- **Multiple bounds:** `<T extends Number & Comparable<T>>`. Only the **first** bound may be a class; the rest must be interfaces.

```java
static double sum(List<? extends Number> numbers) { ... }   // see wildcards
static <T extends Number & Comparable<T>> T biggest(List<T> list) { ... }
```

- A **recursive bound** such as `T extends Comparable<T>` says "T can compare itself with other Ts". It is how `Collections.max` and `Comparable` are written.

---

## 4. Wildcards and PECS

### The surprising rule: generics are invariant

`String` is a subtype of `Object`, but **`List<String>` is not a subtype of `List<Object>`**.

```java
List<String> strings = new ArrayList<>();
// List<Object> objects = strings;      // compile error
```

If that were allowed, you could add an `Integer` through `objects` and break `strings`. (Arrays *are* covariant, which is why `Object[] o = new String[1]; o[0] = 1;` fails only at run time with `ArrayStoreException`.)

### The wildcard `?`

| Form | Name | Reads as | You can... |
|---|---|---|---|
| `List<?>` | unbounded | "a list of something" | read as `Object`; add only `null` |
| `List<? extends Number>` | upper bounded | "a list of Number or any subtype" | **read** as `Number`; cannot add (except `null`) |
| `List<? super Integer>` | lower bounded | "a list of Integer or any supertype" | **add** `Integer`; reads give only `Object` |

```java
static double sum(List<? extends Number> numbers) {     // accepts List<Integer>, List<Double>...
    double total = 0;
    for (Number n : numbers) total += n.doubleValue();
    return total;
}
sum(List.of(1, 2, 3));        // 6.0
sum(List.of(1.5, 2.5));       // 4.0

static void fill(List<? super Integer> sink) {          // accepts List<Integer>, List<Number>, List<Object>
    for (int i = 0; i < 3; i++) sink.add(i);
}
```

### Why you cannot add to `? extends`

`List<? extends Number>` might really be a `List<Integer>`, a `List<Double>`... The compiler cannot know which, so adding a `Double` could corrupt a `List<Integer>`. It forbids all adds.

### PECS: Producer Extends, Consumer Super

> If a parameter **produces** values you read, use `? extends T`. If it **consumes** values you write, use `? super T`.

```java
static <T> void copy(List<? super T> dest, List<? extends T> src) {
    for (T item : src) dest.add(item);      // src produces T, dest consumes T
}

List<Number> numbers = new ArrayList<>();
copy(numbers, List.of(1, 2, 3));            // Integers copied into a List<Number>
```

`Collections.copy`, `Collections.addAll` and `Comparator.comparing` use this pattern. If a parameter both reads and writes, use a plain `T`.

---

## 5. Type Erasure and Its Consequences

Generics exist only at **compile time**. The compiler checks the types, inserts casts, then **erases** the type arguments. At run time `List<String>` and `List<Integer>` are both just `List`.

```java
List<String> a = new ArrayList<>();
List<Integer> b = new ArrayList<>();
System.out.println(a.getClass() == b.getClass());     // true
```

**Why:** backward compatibility. Java 5 generic code had to work with pre-generics libraries.

What the compiler does to `class Box<T> { T value; }`:

| Declared | After erasure |
|---|---|
| `T` | `Object` |
| `T extends Comparable<T>` | `Comparable` (the first bound) |
| `String s = box.get();` | `String s = (String) box.get();` (a cast is inserted) |

**Bridge methods:** when a class implements `Comparable<String>`, the compiler also generates a hidden `compareTo(Object)` that casts and calls your `compareTo(String)`, so overriding still works after erasure.

### What erasure forbids

| You cannot... | Because |
|---|---|
| `new T()` | the type is unknown at run time |
| `new T[10]` or `new List<String>[10]` | arrays need the real element type at run time |
| `obj instanceof List<String>` | only `instanceof List<?>` is legal (reifiable) |
| `T.class` | there is no class object for `T`; pass a `Class<T>` instead |
| `catch (T e)` or `class E<T> extends Exception` | exceptions cannot be generic |
| overload `void f(List<String>)` and `void f(List<Integer>)` | the same signature after erasure |
| `static T value;` | one static field is shared by all instantiations |
| `Box<int>` | type arguments must be reference types |

### Getting the type back: a `Class<T>` token

```java
static <T> T create(Class<T> type) throws Exception {
    return type.getDeclaredConstructor().newInstance();
}
String s = create(String.class);
```

Frameworks use this (`objectMapper.readValue(json, User.class)`), and "super type tokens" (`new TypeReference<List<User>>() {}`) work because a **subclass** records its generic superclass in the class file, which erasure keeps.

### Raw types and heap pollution

```java
List raw = new ArrayList<String>();       // raw type: legal but unchecked
raw.add(42);                              // compiles with a warning
List<String> strings = raw;               // unchecked assignment
String s = strings.get(0);                // ClassCastException here, far from the real mistake
```

Never use raw types in new code. `@SuppressWarnings("unchecked")` should be used on the **smallest** scope possible, with a comment explaining why it is safe. `@SafeVarargs` marks a varargs method with a generic parameter as safe (only on `static`, `final` or `private` methods).

---

## 6. Generics Rules and Restrictions

Quick rules worth knowing:

- **Diamond with anonymous classes** works since Java 9: `new Comparator<>() { ... }`.
- **Inference:** `var map = new HashMap<String, List<Integer>>();` keeps the types; `var list = new ArrayList<>();` infers `ArrayList<Object>`.
- **Generic arrays** are the usual workaround: `(T[]) new Object[n]` with an unchecked cast, or just use `List<T>`.
- **Subtyping with the same arguments:** `ArrayList<String>` is a `List<String>` is a `Collection<String>`. Only the *type argument* is invariant.
- **Static factory with inference:** `List.of(1, 2.5)` infers `List<Number & Comparable<...>>`; `Map.of("a", 1)` infers `Map<String, Integer>`.
- **Primitives:** there is no `List<int>`. For performance-critical code use `int[]` or `IntStream`.
- **Comparable and Comparator:** `class Person implements Comparable<Person>`, and `Comparator.comparing(Person::name)`, rely on generics. See the Collections notes.

---

## 7. Enums: The Basics

An **enum** is a type with a fixed set of named constants.

```java
enum Day { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

Day d = Day.FRIDAY;
if (d == Day.FRIDAY) { }                 // == is correct for enums (each constant is a singleton)
```

**Why not `int` or `String` constants?** `static final int MONDAY = 1;` has no type safety (`setDay(99)` compiles), prints as a bare number, and cannot carry behaviour. An enum is a real type: `setDay(Day d)` accepts only days.

### What every enum gets for free

```java
Day.values();                  // array of all constants, in declaration order
Day.valueOf("FRIDAY");         // Day.FRIDAY; throws IllegalArgumentException if no such name
d.name();                      // "FRIDAY" (the declared name, safest to store)
d.ordinal();                   // 4 (position, starting from 0)
d.toString();                  // "FRIDAY" (can be overridden for display)
d.compareTo(Day.MONDAY);       // difference in ordinal order
```

### Enums in `switch`

```java
switch (d) {
    case SATURDAY, SUNDAY -> System.out.println("weekend");     // note: no "Day." prefix in case labels
    default -> System.out.println("weekday");
}
```

A `switch` **expression** over an enum is exhaustive when it covers every constant, with no `default` needed, so adding a new constant later gives a compile error everywhere you forgot it. That is a good reason to prefer switch expressions over `if` chains.

### What an enum is, underneath

- A special **class** that implicitly extends `java.lang.Enum`, so it **cannot extend another class** (it *can* implement interfaces).
- The constants are `public static final` instances created once, when the class loads.
- The constructor is always **private**; you cannot call `new Day()`.
- An enum is implicitly `final` (unless constants have bodies) and is **thread-safe to initialize**.
- `==` and `equals` are equivalent; `==` also gives compile-time type checking and is `null`-safe on the left.

---

## 8. Enums with Fields, Constructors and Methods

```java
enum Planet {
    MERCURY(3.303e+23, 2.4397e6),
    EARTH(5.976e+24, 6.37814e6);

    private final double mass;       // kg
    private final double radius;     // m

    Planet(double mass, double radius) {      // implicitly private
        this.mass = mass;
        this.radius = radius;
    }

    static final double G = 6.67300E-11;

    double surfaceGravity() { return G * mass / (radius * radius); }
}

System.out.printf("%.2f%n", Planet.EARTH.surfaceGravity());    // 9.80
```

Rules:

- Constants come **first**, separated by commas, ended with a `;` when anything follows.
- Fields should be `final`: constants are shared, so mutable state would be shared too.
- A **lookup** by a field value is common: build a static map once.

```java
enum Status {
    ACTIVE("A"), INACTIVE("I"), DELETED("D");

    private final String code;
    Status(String code) { this.code = code; }
    public String code() { return code; }

    private static final Map<String, Status> BY_CODE = new HashMap<>();
    static { for (Status s : values()) BY_CODE.put(s.code, s); }

    public static Status fromCode(String code) {
        Status s = BY_CODE.get(code);
        if (s == null) throw new IllegalArgumentException("Unknown status code: " + code);
        return s;
    }
}
```

> A static initializer cannot be used from the **constructor** of the same enum (the static map does not exist yet while constants are being created). That is why the map is filled in a `static` block after the constants.

### Never store `ordinal()`

`ordinal()` changes if someone inserts or reorders a constant, silently corrupting saved data. Store `name()` (or an explicit code). In JPA use `@Enumerated(EnumType.STRING)`, not the default `ORDINAL`.

---

## 9. Enums with Behaviour, Interfaces and the Collections

### Constant-specific bodies

Each constant can override a method, replacing `switch` statements with polymorphism:

```java
enum Operation {
    ADD("+") { public double apply(double a, double b) { return a + b; } },
    MULTIPLY("*") { public double apply(double a, double b) { return a * b; } };

    private final String symbol;
    Operation(String symbol) { this.symbol = symbol; }
    public abstract double apply(double a, double b);       // every constant must implement it
    public String symbol() { return symbol; }
}

Operation.MULTIPLY.apply(3, 4);     // 12.0
```

### Enums implementing interfaces

```java
interface Discount { double apply(double price); }

enum Customer implements Discount {
    REGULAR { public double apply(double p) { return p; } },
    GOLD    { public double apply(double p) { return p * 0.9; } };
}
```

An enum cannot extend a class, but it can implement any number of interfaces, so enums can plug into strategy-style designs.

### `EnumMap` and `EnumSet`

Specialised collections for enum keys: faster and smaller than `HashMap`/`HashSet`, and **ordered by declaration order**.

```java
Map<Day, String> plan = new EnumMap<>(Day.class);
plan.put(Day.MONDAY, "gym");
plan.put(Day.FRIDAY, "movie");
System.out.println(plan);                   // {MONDAY=gym, FRIDAY=movie}

Set<Day> weekend = EnumSet.of(Day.SATURDAY, Day.SUNDAY);
Set<Day> weekdays = EnumSet.complementOf(weekend);
Set<Day> all = EnumSet.allOf(Day.class);
Set<Day> midweek = EnumSet.range(Day.TUESDAY, Day.THURSDAY);
```

### Singleton and state machines

```java
enum Config { INSTANCE; private final Properties props = new Properties(); public String get(String k) { return props.getProperty(k); } }
```

An enum with one constant is the simplest **thread-safe, serialization-safe singleton**.

```java
enum OrderState {
    NEW { OrderState next() { return PAID; } },
    PAID { OrderState next() { return SHIPPED; } },
    SHIPPED { OrderState next() { return SHIPPED; } };
    abstract OrderState next();
}
```

### Useful details

- `Enum.valueOf(Day.class, "MONDAY")` works with a `Class` token.
- Enums are `Comparable` (by ordinal) and `Serializable` (by name, so the constant identity survives).
- `switch` on an enum compiles to an array lookup on the ordinal: fast.
- In Spring request parameters and JSON, enums bind by **name**; an unknown name gives a 400 error. Provide a tolerant `fromCode` when clients may send other values.

---

## 10. Nested Classes: The Four Kinds

A class declared inside another class is **nested**. There are four kinds.

| Kind | Declared | Needs an outer instance? | Typical use |
|---|---|---|---|
| **Static nested** | `static class N {}` inside a class | No | helper tied to the outer class: `Builder`, `Entry` |
| **Inner (member)** | `class I {}` inside a class, no `static` | **Yes** | an object that belongs to one outer object: iterators |
| **Local** | declared inside a method or block | Yes (in instance methods) | a small helper used in one method |
| **Anonymous** | `new Type() { ... }` | Yes (in instance code) | a one-off implementation, mostly replaced by lambdas |

**Why nest?** To group a class that is used only by its outer class (better encapsulation, less clutter), and to give it access to the outer class's private members.

```java
public class Outer {
    private int secret = 42;

    static class StaticNested {                  // no access to instance fields of Outer
        void hello() { System.out.println("static nested"); }
    }

    class Inner {                                // has an implicit reference to an Outer
        int reveal() { return secret; }          // reads Outer's private field directly
    }
}

Outer.StaticNested s = new Outer.StaticNested();
Outer outer = new Outer();
Outer.Inner i = outer.new Inner();               // needs an Outer instance
```

### Static nested: the Builder pattern

```java
public class Pizza {
    private final String size;
    private final boolean cheese;
    private Pizza(Builder b) { this.size = b.size; this.cheese = b.cheese; }

    public static class Builder {
        private String size = "medium";
        private boolean cheese;
        public Builder size(String s) { this.size = s; return this; }
        public Builder cheese(boolean c) { this.cheese = c; return this; }
        public Pizza build() { return new Pizza(this); }
    }
}

Pizza p = new Pizza.Builder().size("large").cheese(true).build();
```

`Map.Entry` is a static nested interface; `AbstractMap.SimpleEntry` is a static nested class.

### How they compile

Each nested class becomes its **own `.class` file** named `Outer$Inner.class` (anonymous ones are `Outer$1.class`). The compiler adds a hidden `this$0` field to inner classes holding the outer reference.

---

## 11. Inner Classes in Depth

### The hidden reference to the outer object

An inner class instance **cannot exist without** an outer instance. It can use all of the outer's members, including `private` ones, and the outer can use the inner's private members too.

```java
public class Counter {
    private int count = 0;

    class Incrementer {
        void inc() { count++; }                  // changes the OUTER object's field
    }
}
Counter c = new Counter();
Counter.Incrementer inc = c.new Incrementer();
inc.inc();
```

### `this` vs `Outer.this`

```java
class Outer {
    int x = 1;
    class Inner {
        int x = 2;
        void show() {
            int x = 3;
            System.out.println(x);               // 3  local variable
            System.out.println(this.x);          // 2  Inner's field
            System.out.println(Outer.this.x);    // 1  Outer's field
        }
    }
}
```

### Typical use: an iterator that reads the list's private data

```java
class Bag<T> implements Iterable<T> {
    private final Object[] items = new Object[10];
    private int size;
    void add(T t) { items[size++] = t; }

    public Iterator<T> iterator() {
        return new Iterator<>() {                // anonymous inner class
            int i = 0;
            public boolean hasNext() { return i < size; }
            @SuppressWarnings("unchecked")
            public T next() { return (T) items[i++]; }
        };
    }
}
```

### The memory-leak trap

Because every inner instance holds its outer instance, a long-lived inner object keeps the **whole outer object alive**. If the outer is large (an Activity, a request context), an unused one can never be collected.

> **Rule:** if the nested class does not need the outer instance, make it `static`. IDEs and linters flag this. Most nested classes should be `static`.

### Restrictions

- Inner classes could not have `static` members before Java 16; since Java 16 they can.
- A **static** nested class can be instantiated alone and can only use the outer's **static** members directly.
- An inner class that extends the same outer class creates confusing recursion; avoid it.

---

## 12. Local and Anonymous Classes, and How Lambdas Differ

### Local class

```java
static List<String> shout(List<String> words) {
    class Shouter {                              // visible only inside this method
        String go(String w) { return w.toUpperCase() + "!"; }
    }
    Shouter s = new Shouter();
    List<String> out = new ArrayList<>();
    for (String w : words) out.add(s.go(w));
    return out;
}
```

A local class can use the method's local variables and parameters only if they are **effectively final** (never reassigned). Java copies their values into the object when it is created, so a later change could not be seen; the rule prevents confusion.

```java
int base = 10;
// base++;                     // would break the lambda/class below
Runnable r = () -> System.out.println(base);
```

Since Java 16, **records, enums and interfaces** can also be declared locally.

### Anonymous class

An unnamed class that implements an interface or extends a class, defined and instantiated at once.

```java
Comparator<String> byLength = new Comparator<String>() {
    @Override public int compare(String a, String b) { return a.length() - b.length(); }
};

Thread t = new Thread() {                        // extends Thread
    @Override public void run() { System.out.println("run"); }
};
```

Limits: no constructor of its own (use an instance initializer block), one use only, cannot be reused by name.

### Anonymous class vs lambda

| | Anonymous class | Lambda |
|---|---|---|
| Works for | any interface **or class**, even with several abstract methods | only a **functional interface** (one abstract method) |
| `this` means | the anonymous object itself | the **enclosing** object |
| Has its own fields/state | yes | no |
| Generated class | a real `Outer$1.class` | none (uses `invokedynamic`) |
| Variable shadowing | can redeclare a name from the enclosing scope | cannot |

```java
class Demo {
    String name = "demo";
    void run() {
        Runnable anon = new Runnable() {
            String name = "anon";
            public void run() { System.out.println(this.name); }    // anon
        };
        Runnable lambda = () -> System.out.println(this.name);      // demo
        anon.run();
        lambda.run();
    }
}
```

Prefer a lambda (or method reference) for a single-method interface; keep an anonymous class when you need state, several methods (like `Iterator`), or a class to extend.

---

## 13. Common Mistakes

| Mistake | Why it is wrong | Fix |
|---|---|---|
| Using raw types (`List list`) | no type safety, unchecked warnings | always `List<String>` |
| `List<Object> o = new ArrayList<String>();` | generics are invariant | `List<?>` or `List<? extends Object>` |
| Adding to a `List<? extends T>` | the real element type is unknown | use `? super T` for writing |
| `new T()` or `new T[n]` | erased at run time | pass a `Supplier<T>` / `Class<T>` or use `List<T>` |
| `instanceof List<String>` | erased | `instanceof List<?>` |
| Overloading on `List<String>` vs `List<Integer>` | same erasure | different names |
| Comparing enums with `.equals` thinking it differs | works, but `==` is clearer and null-safe on the left | use `==` |
| Storing `ordinal()` in a database | breaks when constants are reordered | store `name()` or a code |
| `@Enumerated` left as default `ORDINAL` | same problem | `EnumType.STRING` |
| `Enum.valueOf` on user input without handling the exception | `IllegalArgumentException` | catch it or use a lookup map |
| Inner class that does not need the outer | hidden reference, possible leak | make it `static` |
| Modifying a captured local variable | must be effectively final | use an `AtomicInteger` or a field |
| Calling an enum's static field from its constructor | not initialized yet | fill static maps in a `static` block after the constants |

---

## 14. Output Prediction Puzzles

**Puzzle 1: same class at run time?**

```java
List<String> a = new ArrayList<>();
List<Integer> b = new ArrayList<>();
System.out.println(a.getClass() == b.getClass());
System.out.println(a instanceof ArrayList<?>);
```

**Puzzle 2: a heap-pollution surprise**

```java
List raw = new ArrayList<String>();
raw.add(42);
List<String> strings = raw;
System.out.println(strings.size());
try {
    String s = strings.get(0);
} catch (ClassCastException e) {
    System.out.println("CCE");
}
```

**Puzzle 3: enum basics**

```java
enum Color { RED, GREEN, BLUE }
System.out.println(Color.GREEN.ordinal());
System.out.println(Color.valueOf("BLUE"));
System.out.println(Color.RED.compareTo(Color.BLUE));
System.out.println(Color.values().length);
```

**Puzzle 4: `valueOf` with the wrong case**

```java
enum Color { RED, GREEN }
try {
    Color.valueOf("red");
} catch (IllegalArgumentException e) {
    System.out.println("no such constant");
}
```

**Puzzle 5: `EnumMap` order**

```java
enum Day { MON, TUE, WED }
Map<Day, Integer> m = new EnumMap<>(Day.class);
m.put(Day.WED, 3);
m.put(Day.MON, 1);
System.out.println(m);
```

**Puzzle 6: `this` in a lambda vs an anonymous class**

```java
class Demo {
    String name = "demo";
    void run() {
        Runnable anon = new Runnable() {
            String name = "anon";
            public void run() { System.out.println(this.name); }
        };
        Runnable lambda = () -> System.out.println(this.name);
        anon.run();
        lambda.run();
    }
}
new Demo().run();
```

**Puzzle 7: `this`, `Outer.this` and a local**

```java
class Outer {
    int x = 1;
    class Inner {
        int x = 2;
        void show() {
            int x = 3;
            System.out.println(x + " " + this.x + " " + Outer.this.x);
        }
    }
}
new Outer().new Inner().show();
```

**Puzzle 8: a generic method and bounds**

```java
static <T extends Comparable<T>> T max(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
System.out.println(max(3, 7));
System.out.println(max("pear", "apple"));
System.out.println(max(2.5, 2.25));
```

**Puzzle 9: arrays are covariant, generics are not**

```java
Object[] objs = new String[1];
try {
    objs[0] = 42;
} catch (ArrayStoreException e) {
    System.out.println("ASE");
}
```

**Answers**

1. **`true`**, **`true`**. After erasure both are `ArrayList`. `instanceof ArrayList<?>` is allowed (an unbounded wildcard is reifiable).
2. **`1`**, then **`CCE`**. The unchecked add works (no check at run time), the size is `1`, and the inserted cast to `String` fails only when the element is read.
3. **`1`**, **`BLUE`**, **`-2`**, **`3`**. Ordinals start at 0; `compareTo` is the difference of ordinals (0 - 2).
4. **`no such constant`**. `valueOf` is case-sensitive and throws `IllegalArgumentException`.
5. **`{MON=1, WED=3}`**. An `EnumMap` iterates in declaration order, not insertion order.
6. **`anon`**, then **`demo`**. In an anonymous class `this` is the anonymous object; in a lambda `this` is the enclosing `Demo`.
7. **`3 2 1`**. The local, `this.x` (Inner's field) and `Outer.this.x` (Outer's field).
8. **`7`**, **`pear`**, **`2.5`**. `int` and `double` are boxed to `Integer`/`Double`, which are `Comparable`; `"pear"` is greater than `"apple"`.
9. **`ASE`**. The array knows its real type (`String[]`) at run time, so the store fails, an `ArrayStoreException`. The generic equivalent (`List<Object> = List<String>`) is rejected at compile time instead.

---

## 15. Interview Questions

**1. What are generics and why were they added?**
Type parameters that let classes and methods work on many types while keeping compile-time type safety. They remove casts and turn `ClassCastException`s into compile errors.

**2. What is type erasure?**
The compiler removes type arguments after checking, replacing `T` by its bound (or `Object`) and inserting casts. At run time `List<String>` and `List<Integer>` are the same class. It was chosen for backward compatibility.

**3. Why can't you create `new T()` or `new T[]`?**
Because `T` does not exist at run time. Workarounds: pass a `Class<T>` or `Supplier<T>`, or cast `(T[]) new Object[n]`.

**4. What is the difference between `List<?>`, `List<Object>` and a raw `List`?**
`List<Object>` holds any object and accepts adds, but is not a supertype of `List<String>`. `List<?>` is a list of an unknown type (read-only in practice). A raw `List` switches off type checking entirely.

**5. Explain PECS.**
Producer Extends, Consumer Super: use `? extends T` when you only read from a structure, `? super T` when you only write to it.

**6. Is `List<String>` a subtype of `List<Object>`?**
No: generics are invariant. Use `List<? extends Object>` to accept both.

**7. What is a bounded type parameter?**
`<T extends Number>` restricts `T` to a type or its subtypes and lets you call the bound's methods. Multiple bounds use `&`, with the class first.

**8. Can an enum extend a class or implement an interface?**
It cannot extend a class (it already extends `java.lang.Enum`) but it can implement interfaces.

**9. Why use `==` to compare enums?**
Each constant is a singleton, so `==` is correct, `null`-safe on the left, and type-checked at compile time.

**10. How do you make a thread-safe singleton with an enum?**
`enum S { INSTANCE; }`. The JVM guarantees one instance, safe initialization, and protection against reflection and serialization attacks.

**11. Difference between `EnumMap` and `HashMap`?**
`EnumMap` uses an array indexed by ordinal: faster, smaller, ordered by declaration, keys must be of one enum type.

**12. Static nested class vs inner class?**
A static nested class needs no outer instance and cannot use the outer's instance members. An inner class holds a reference to an outer instance and can use its members; prefer `static` unless you need that reference.

**13. Why should enums not be persisted by ordinal?**
Reordering or inserting constants changes the numbers and corrupts stored data. Persist the name or a stable code.

**14. How is a lambda different from an anonymous class?**
A lambda only implements a functional interface, has no `this` of its own (it refers to the enclosing object), is not a separate class file, and cannot hold state; an anonymous class can do all of that.

**15. What is a bridge method?**
A synthetic method the compiler adds so a generic override still works after erasure, for example `compareTo(Object)` calling `compareTo(String)`.

**16. What does "effectively final" mean?**
A local variable that is never reassigned after initialization. Lambdas, local classes and anonymous classes can capture only such variables.

---

## 16. Cheat Sheet

| Topic | Remember |
|---|---|
| Declare | `class Box<T>`, `<T> T first(List<T> l)` |
| Bounds | `<T extends A & B>`: class first, interfaces after |
| Invariance | `List<String>` is not a `List<Object>`; arrays are covariant |
| Wildcards | `?` unknown, `? extends T` read, `? super T` write (PECS) |
| Erasure | no `new T`, no `T[]`, no `instanceof List<String>`, no generic exceptions |
| Raw types | avoid; they bypass checks |
| Enum basics | `values()`, `valueOf()`, `name()`, `ordinal()`; compare with `==` |
| Enum data | fields + private constructor; lookup map filled in a `static` block |
| Enum limits | cannot extend a class; can implement interfaces |
| Persistence | store `name()` / code, `@Enumerated(STRING)` |
| Collections | `EnumMap`, `EnumSet` (declaration order, fast) |
| Nested kinds | static nested, inner, local, anonymous |
| Prefer | `static` nested unless the outer instance is needed |
| Capture | local variables must be effectively final |
| `this` | anonymous class: itself; lambda: the enclosing object |

---

## 17. Practice Tasks with Solutions

**Task 1.** Write a generic `Stack<T>` with `push`, `pop`, `peek`, `isEmpty`, throwing a clear exception when empty.

```java
class Stack<T> {
    private final Deque<T> items = new ArrayDeque<>();
    void push(T t) { items.push(t); }
    T pop() {
        if (items.isEmpty()) throw new NoSuchElementException("stack is empty");
        return items.pop();
    }
    T peek() {
        if (items.isEmpty()) throw new NoSuchElementException("stack is empty");
        return items.peek();
    }
    boolean isEmpty() { return items.isEmpty(); }
}
```

**Task 2.** Write `static <T extends Comparable<T>> T maxOf(List<T> list)`.

```java
static <T extends Comparable<T>> T maxOf(List<T> list) {
    if (list.isEmpty()) throw new NoSuchElementException();
    T best = list.get(0);
    for (T t : list) if (t.compareTo(best) > 0) best = t;
    return best;
}
// maxOf(List.of(3, 9, 4)) is 9;  maxOf(List.of("kiwi", "apple")) is "kiwi"
```

**Task 3.** Sum any list of numbers, then fill a list of `Number` with integers, using the right wildcard in each direction.

```java
static double sum(List<? extends Number> xs) {
    double t = 0;
    for (Number n : xs) t += n.doubleValue();
    return t;
}
static void addInts(List<? super Integer> sink, int n) {
    for (int i = 1; i <= n; i++) sink.add(i);
}
List<Number> nums = new ArrayList<>();
addInts(nums, 3);
System.out.println(sum(nums));                // 6.0
```

**Task 4.** Design an enum `HttpStatus` with a code and a reason, a lookup by code, and a method `isError()`.

```java
enum HttpStatus {
    OK(200, "OK"), NOT_FOUND(404, "Not Found"), SERVER_ERROR(500, "Internal Server Error");

    private final int code;
    private final String reason;
    HttpStatus(int code, String reason) { this.code = code; this.reason = reason; }

    private static final Map<Integer, HttpStatus> BY_CODE = new HashMap<>();
    static { for (HttpStatus s : values()) BY_CODE.put(s.code, s); }

    static HttpStatus of(int code) {
        HttpStatus s = BY_CODE.get(code);
        if (s == null) throw new IllegalArgumentException("Unknown status " + code);
        return s;
    }
    boolean isError() { return code >= 400; }
    int code() { return code; }
    String reason() { return reason; }
}
// HttpStatus.of(404).isError() is true
```

**Task 5.** Replace a `switch` on a state with an enum whose constants carry behaviour (a traffic light that returns the next light and its seconds).

```java
enum Light {
    GREEN(30) { Light next() { return YELLOW; } },
    YELLOW(5) { Light next() { return RED; } },
    RED(20) { Light next() { return GREEN; } };

    final int seconds;
    Light(int seconds) { this.seconds = seconds; }
    abstract Light next();
}
// Light.RED.next() is GREEN; Light.GREEN.seconds is 30
```

**Task 6.** Convert this inner class to a static nested class, and explain why that is better.

```java
// before: class Library { private List<Book> books; class Shelf { ... } }   (Shelf never uses books)
class Library {
    private final List<String> books = new ArrayList<>();
    static class Shelf {                    // no outer reference, so a Shelf never keeps a Library alive
        final String label;
        Shelf(String label) { this.label = label; }
    }
}
Library.Shelf s = new Library.Shelf("A1");     // no Library instance needed
```

**Task 7.** Use `EnumMap` to count how many times each enum value occurs in a list.

```java
enum Level { LOW, MEDIUM, HIGH }

static Map<Level, Integer> count(List<Level> list) {
    Map<Level, Integer> counts = new EnumMap<>(Level.class);
    for (Level l : list) counts.merge(l, 1, Integer::sum);
    return counts;
}
// count(List.of(HIGH, LOW, HIGH)) is {LOW=1, HIGH=2}  (declaration order)
```

*End of notes.*
