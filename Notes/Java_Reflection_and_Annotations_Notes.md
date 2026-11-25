# Java Reflection and Annotations: Detailed Notes

**Reflection** lets a program look at its own classes at run time: list their fields and methods, create objects and call methods **by name**. **Annotations** attach metadata to code. Together they are the machinery behind Spring (`@Component`, `@Autowired`, `@GetMapping`), JPA (`@Entity`, `@Column`), JUnit (`@Test`), Jackson and Lombok. These notes explain how they work by building small versions of those frameworks yourself.

> All code was compiled and run on Java 21.

---

## Table of Contents

1. [Why Reflection Exists](#1-why-reflection-exists)
2. [Getting a `Class` Object](#2-getting-a-class-object)
3. [Inspecting Classes, Fields, Methods and Constructors](#3-inspecting-classes-fields-methods-and-constructors)
4. [Creating Objects and Calling Methods](#4-creating-objects-and-calling-methods)
5. [Private Members, `setAccessible` and the Module System](#5-private-members-setaccessible-and-the-module-system)
6. [Generics and Reflection](#6-generics-and-reflection)
7. [Annotations: What They Are](#7-annotations-what-they-are)
8. [Writing Your Own Annotation](#8-writing-your-own-annotation)
9. [Reading Annotations at Run Time](#9-reading-annotations-at-run-time)
10. [Project: A Tiny Validator (How Bean Validation Works)](#10-project-a-tiny-validator-how-bean-validation-works)
11. [Project: A Tiny Dependency Injection Container (How Spring Works)](#11-project-a-tiny-dependency-injection-container-how-spring-works)
12. [Compile-Time Annotation Processing](#12-compile-time-annotation-processing)
13. [Costs, Risks and Alternatives](#13-costs-risks-and-alternatives)
14. [Common Mistakes](#14-common-mistakes)
15. [Output Prediction Puzzles](#15-output-prediction-puzzles)
16. [Interview Questions](#16-interview-questions)
17. [Cheat Sheet](#17-cheat-sheet)
18. [Practice Tasks](#18-practice-tasks)

---

## 1. Why Reflection Exists

Normally the compiler knows every class and method your code uses. Frameworks cannot: **Spring does not know your `OrderService` when it is compiled**, Jackson does not know your `User` class, JUnit does not know your test names. They discover your code **at run time** and act on it:

| Framework | What reflection lets it do |
|---|---|
| Spring | scan for `@Component`s, create them, inject `@Autowired` fields and constructor arguments |
| Jackson / Gson | read the fields or getters of any object and write JSON; build an object from JSON |
| Hibernate / JPA | map fields to columns, create entity instances, set fields from a `ResultSet` |
| JUnit | find methods annotated `@Test` and call them |
| Bean Validation | read `@NotBlank`, `@Min` and check fields |
| Serialization libraries, test mocks (Mockito), debuggers, IDE tools | inspect and build objects generically |

The cost: reflection bypasses compile-time checks, is slower than direct calls, and can break encapsulation. Use it to build **generic infrastructure**, not in ordinary business code.

---

## 2. Getting a `Class` Object

Every type has exactly one `Class<?>` object describing it. Three ways to get it:

```java
Class<String> a = String.class;                           // 1. the class literal: known at compile time
Class<?> b = "hello".getClass();                          // 2. from an instance: the RUNTIME type
Class<?> c = Class.forName("java.util.ArrayList");        // 3. by name (a String): throws ClassNotFoundException
```

| | `.class` | `getClass()` | `Class.forName` |
|---|---|---|---|
| Needs | the type in source | an object | just a name |
| Typical use | tokens like `User.class` | find the real type behind a reference | plugins, config-driven classes, old JDBC drivers |

```java
Object o = new ArrayList<String>();
System.out.println(o.getClass().getName());         // java.util.ArrayList
System.out.println(o.getClass().getSimpleName());   // ArrayList
System.out.println(int.class + " " + int[].class.getName() + " " + void.class);   // int [I void
```

`getClass()` returns the **actual** class (a `List` reference may hold an `ArrayList`). Primitives and arrays have `Class` objects too (`int.class`, `String[].class`).

---

## 3. Inspecting Classes, Fields, Methods and Constructors

```java
class Person {
    public String name;
    private int age;
    static final String SPECIES = "human";

    public Person() { }
    public Person(String name, int age) { this.name = name; this.age = age; }

    public String greet(String other) { return "Hi " + other + ", I am " + name; }
    private int secretNumber() { return 42; }
}

Class<?> c = Person.class;
c.getSimpleName();                       // Person
c.getSuperclass();                       // class java.lang.Object
c.getInterfaces();                       // []
Modifier.isPublic(c.getModifiers());     // false (package-private here)
c.getPackageName();                      // the package
```

### Fields, methods, constructors

```java
for (Field f : c.getDeclaredFields())
    System.out.println(Modifier.toString(f.getModifiers()) + " " + f.getType().getSimpleName() + " " + f.getName());
// public String name
// private int age
// static final String SPECIES

for (Method m : c.getDeclaredMethods())
    System.out.println(m.getName() + " " + m.getParameterCount());      // greet 1, secretNumber 0  (order is not guaranteed)

for (Constructor<?> k : c.getDeclaredConstructors())
    System.out.println(k.getParameterCount());                          // 0 and 2
```

| Method | Returns |
|---|---|
| `getFields()`, `getMethods()`, `getConstructors()` | **public** members, **including inherited** ones |
| `getDeclaredFields()`, `getDeclaredMethods()`, `getDeclaredConstructors()` | **all** members declared **in this class** (private too), **not** inherited |
| `getField(name)`, `getDeclaredField(name)` | one field (throws `NoSuchFieldException`) |
| `getMethod(name, paramTypes...)`, `getDeclaredMethod(...)` | one method (throws `NoSuchMethodException`) |
| `getConstructor(paramTypes...)` | one constructor |

To see **private members of a parent class** you must walk up: `for (Class<?> k = c; k != null; k = k.getSuperclass()) k.getDeclaredFields()`.

### Records, enums and others

```java
record Point(int x, int y) { }
for (RecordComponent rc : Point.class.getRecordComponents())
    System.out.println(rc.getName() + ":" + rc.getType());       // x:int  y:int

Day.class.getEnumConstants();                                     // all constants of an enum
c.isInterface(); c.isEnum(); c.isRecord(); c.isArray(); c.isAnnotation(); c.isPrimitive();
```

---

## 4. Creating Objects and Calling Methods

```java
Class<?> c = Person.class;

// create
Person p = (Person) c.getDeclaredConstructor(String.class, int.class).newInstance("Asha", 30);

// call a method by name
Method greet = c.getMethod("greet", String.class);
Object result = greet.invoke(p, "Ben");                  // "Hi Ben, I am Asha"

// read and write a field by name
Field name = c.getField("name");
name.get(p);                                             // "Asha"
name.set(p, "Chitra");

// static members: pass null as the instance
Field species = c.getDeclaredField("SPECIES");
species.get(null);                                       // "human"
Math.class.getMethod("max", int.class, int.class).invoke(null, 3, 9);   // 9
```

Details that bite:

- `invoke` takes and returns **`Object`**: primitives are boxed and unboxed for you.
- `getMethod("max", int.class, int.class)` needs the **exact parameter types** (`int.class`, not `Integer.class`). A wrong type gives `NoSuchMethodException`.
- Exceptions thrown **inside** the called method arrive wrapped in an **`InvocationTargetException`**: call `e.getCause()` to get the real one.

```java
try {
    Person.class.getMethod("greet", String.class).invoke(null, "x");     // instance method, null instance
} catch (NullPointerException e) { }
// a method that throws:
try { someMethod.invoke(obj); }
catch (InvocationTargetException e) { throw e.getCause(); }              // unwrap
```

- You can create an instance from a name in a config file: `Class.forName(className).getDeclaredConstructor().newInstance()` (the old `Class.newInstance()` is deprecated).
- Arrays: `Array.newInstance(int.class, 5)`, `Array.set(array, i, v)`.

---

## 5. Private Members, `setAccessible` and the Module System

```java
Field age = Person.class.getDeclaredField("age");
// age.get(p);                         // IllegalAccessException: private
age.setAccessible(true);               // switch off the access check for this reflected object
age.get(p);                            // 30
age.set(p, 31);

Method secret = Person.class.getDeclaredMethod("secretNumber");
secret.setAccessible(true);
secret.invoke(p);                      // 42
```

This breaks encapsulation: it is how JPA sets private fields, how Mockito `@InjectMocks` and many test tools work, and why a `private` modifier is **not a security boundary**.

### The module system (Java 9+, enforced since Java 17)

Code inside the **JDK** is in modules that do not open their internals. Reflecting into them fails:

```java
Field f = String.class.getDeclaredField("value");
f.setAccessible(true);
// InaccessibleObjectException: Unable to make field private final byte[] java.lang.String.value accessible:
//   module java.base does not "opens java.lang" to unnamed module
```

Java 16 made this the default (`--illegal-access` was removed in 17). Frameworks that need access to **your** classes work because application code is in the unnamed module (everything open) or you declare `opens com.example.model to jackson.databind;` in `module-info.java`. For old libraries on new JDKs the workaround is `--add-opens java.base/java.lang=ALL-UNNAMED`, a sign the library needs an update.

Final fields: `setAccessible(true)` then `set` works on **instance** `final` fields in many cases but the JIT may have inlined the value; static final constants are not changeable. Treat modifying `final` through reflection as undefined behaviour.

---

## 6. Generics and Reflection

Generics are **erased** at run time (see the Generics notes), so `list.getClass()` is just `ArrayList`. But **declarations** keep their generic signature in the class file, so you can read the type arguments of a **field, method return or parameter**:

```java
class Library { public List<String> titles; public Map<String, Integer> stock; }

Field f = Library.class.getField("titles");
ParameterizedType t = (ParameterizedType) f.getGenericType();
System.out.println(t.getRawType());                    // interface java.util.List
System.out.println(t.getActualTypeArguments()[0]);     // class java.lang.String
```

This is how **Jackson's `TypeReference<List<User>>` and Gson's `TypeToken`** work: an anonymous subclass `new TypeReference<List<User>>() {}` records its generic superclass, which survives erasure. It is also how Spring Data finds that `UserRepository extends JpaRepository<User, Long>` is about `User`.

---

## 7. Annotations: What They Are

An **annotation** is metadata attached to a declaration (class, method, field, parameter, package...). It does nothing by itself: something else (the compiler, a build tool, or a framework using reflection) reads it and acts.

```java
@Entity                                  // a marker
@Table(name = "users")                   // with an element
class User {
    @Id @GeneratedValue                  // several on one field
    private Long id;

    @Column(name = "email", nullable = false, length = 120)
    private String email;
}
```

### The built-in annotations you already use

| Annotation | Read by | Effect |
|---|---|---|
| `@Override` | compiler | error if the method does not really override |
| `@Deprecated` (`forRemoval = true`) | compiler | warning (or stronger) when used |
| `@SuppressWarnings("unchecked")` | compiler | silence a specific warning |
| `@FunctionalInterface` | compiler | error if the interface has not exactly one abstract method |
| `@SafeVarargs` | compiler | marks a generic varargs method as safe |

### Where annotations are kept: retention

| `RetentionPolicy` | Kept in | Visible at run time? | Used by |
|---|---|---|---|
| `SOURCE` | source only, dropped by the compiler | no | `@Override`, Lombok, annotation processors |
| `CLASS` (the default) | the `.class` file | no | bytecode tools |
| `RUNTIME` | the `.class` file **and** loaded into the JVM | **yes, via reflection** | Spring, JPA, JUnit, Jackson |

---

## 8. Writing Your Own Annotation

```java
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)             // keep it so reflection can see it
@Target(ElementType.FIELD)                      // may only be placed on fields
@Documented
public @interface Length {
    int min() default 0;                        // an "element": looks like a method, used like a named value
    int max();                                  // no default: required
    String message() default "length out of range";
}

class Signup {
    @Length(min = 3, max = 20) String username;
    @Length(max = 8, message = "password too long") String password;
}
```

| Meta-annotation | Purpose |
|---|---|
| `@Retention` | how long it is kept (above); **forgetting `RUNTIME` is the number-one reason "my annotation is not found"** |
| `@Target` | where it may appear: `TYPE`, `FIELD`, `METHOD`, `PARAMETER`, `CONSTRUCTOR`, `ANNOTATION_TYPE`, `RECORD_COMPONENT`... |
| `@Inherited` | subclasses of an annotated **class** inherit it (not for methods or interfaces) |
| `@Repeatable` | allow the annotation more than once on one element |
| `@Documented` | include it in the Javadoc |

Rules for elements: the types are limited to primitives, `String`, `Class`, enums, other annotations, and arrays of these; the values must be **compile-time constants**; the single element named `value` can be written without its name (`@SuppressWarnings("unchecked")`).

A **marker annotation** has no elements (`@Test`). A **meta-annotation** is an annotation on an annotation: Spring's `@RestController` is itself annotated `@Controller` and `@ResponseBody`, so one annotation brings several meanings (a "composed annotation").

---

## 9. Reading Annotations at Run Time

```java
Field f = Signup.class.getDeclaredField("username");
Length len = f.getAnnotation(Length.class);              // null if absent
System.out.println(len.min() + ".." + len.max());        // 3..20

f.isAnnotationPresent(Length.class);                      // true
for (Annotation a : f.getAnnotations()) System.out.println(a.annotationType().getSimpleName());

Class<?> c = Signup.class;
c.getAnnotation(Entity.class);                            // class-level
Method m = c.getMethod("save");
m.getAnnotation(Transactional.class);                     // method-level
m.getParameterAnnotations();                              // Annotation[][]: one array per parameter
```

Spring adds `AnnotatedElementUtils` and `AnnotationUtils.findAnnotation(...)`, which also look through **meta-annotations** and class hierarchies (plain reflection sees only an annotation placed directly on the element).

---

## 10. Project: A Tiny Validator (How Bean Validation Works)

Hibernate Validator's `@NotBlank`, `@Min`, `@Size` work like this. A small version:

```java
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface NotBlank { String message() default "must not be blank"; }

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface Range { int min(); int max(); }

class Validator {
    static List<String> validate(Object bean) throws IllegalAccessException {
        List<String> errors = new ArrayList<>();
        for (Field f : bean.getClass().getDeclaredFields()) {
            f.setAccessible(true);                                   // fields may be private
            Object value = f.get(bean);

            NotBlank nb = f.getAnnotation(NotBlank.class);
            if (nb != null && (value == null || value.toString().isBlank()))
                errors.add(f.getName() + " " + nb.message());

            Range r = f.getAnnotation(Range.class);
            if (r != null && value instanceof Integer n && (n < r.min() || n > r.max()))
                errors.add(f.getName() + " must be between " + r.min() + " and " + r.max());
        }
        return errors;
    }
}

class Student {
    @NotBlank String name;
    @Range(min = 18, max = 60) int age;
    Student(String name, int age) { this.name = name; this.age = age; }
}

System.out.println(Validator.validate(new Student("Asha", 25)));   // []
System.out.println(Validator.validate(new Student(" ", 12)));      // [name must not be blank, age must be between 18 and 60]
```

That is the whole idea: **scan fields, read annotations, apply rules**. The real library adds groups, nested objects, i18n messages and a rule registry. In Spring, `@Valid` on a controller parameter triggers it, and the failure becomes a `MethodArgumentNotValidException` (400).

---

## 11. Project: A Tiny Dependency Injection Container (How Spring Works)

Spring's core: find classes, create them, **inject what they need**. A 30-line version:

```java
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
@interface Component { }

interface Notifier { String send(String m); }

@Component class SmsNotifier implements Notifier {
    public String send(String m) { return "sms: " + m; }
}

@Component class SignupService {
    private final Notifier notifier;
    SignupService(Notifier notifier) { this.notifier = notifier; }              // the container will supply this
    String signUp(String user) { return notifier.send("welcome " + user); }
}

class MiniContainer {
    private final Map<Class<?>, Object> beans = new HashMap<>();
    private final List<Class<?>> components;

    MiniContainer(Class<?>... components) { this.components = List.of(components); }

    @SuppressWarnings("unchecked")
    <T> T get(Class<T> type) {
        // an existing bean? (also matches by interface: a Notifier request finds SmsNotifier)
        for (var e : beans.entrySet())
            if (type.isAssignableFrom(e.getKey())) return (T) e.getValue();

        Class<?> impl = components.stream()
            .filter(c -> type.isAssignableFrom(c) && c.isAnnotationPresent(Component.class))
            .findFirst().orElseThrow(() -> new IllegalStateException("no @Component for " + type.getSimpleName()));

        try {
            Constructor<?> ctor = impl.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            Object[] args = Arrays.stream(ctor.getParameterTypes()).map(this::get).toArray();   // recursion: build dependencies first
            Object bean = ctor.newInstance(args);
            beans.put(impl, bean);                                                               // a singleton
            return (T) bean;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot create " + impl.getSimpleName(), e);
        }
    }
}

MiniContainer ctx = new MiniContainer(SmsNotifier.class, SignupService.class);
System.out.println(ctx.get(SignupService.class).signUp("asha"));     // sms: welcome asha
System.out.println(ctx.get(SignupService.class) == ctx.get(SignupService.class));   // true: one singleton
```

What it shows, and what Spring adds:

| Mini container | Real Spring |
|---|---|
| given the classes | **classpath scanning** (`@ComponentScan`) finds `@Component`/`@Service`/`@Repository` on its own |
| `isAnnotationPresent(Component.class)` | meta-annotations: `@Service` is annotated with `@Component` |
| constructor injection by parameter type | plus `@Autowired` fields/setters, `@Qualifier`, `@Primary`, generics |
| one singleton map | scopes (`singleton`, `prototype`, `request`), lifecycle callbacks (`@PostConstruct`), circular-dependency handling |
| plain objects | **proxies** around beans for `@Transactional`, `@Async`, AOP |

When an app fails with *"No qualifying bean of type X"* it is this `orElseThrow` in a much larger form.

---

## 12. Compile-Time Annotation Processing

Not all annotation magic is reflection. An **annotation processor** runs **inside the compiler**, reads the annotations on your source and **generates new source files** (so there is no run-time cost and no reflection).

| Tool | What it generates |
|---|---|
| **Lombok** (`@Getter`, `@Builder`, `@Slf4j`) | modifies the compiled class (an unusual, non-standard processor) |
| **MapStruct** (`@Mapper`) | plain mapping code between DTOs and entities |
| **Dagger** | dependency-injection wiring at compile time |
| JPA metamodel (`hibernate-processor`, formerly `hibernate-jpamodelgen`) | `User_` classes for type-safe criteria queries |
| **Spring AOT** | for GraalVM native images: replaces run-time scanning and reflection with generated code |

Annotations with `RetentionPolicy.SOURCE` or `CLASS` are for these tools. If you annotate for a processor and see no effect, check that the processor is on the compiler's path (`annotationProcessorPaths` in Maven) and that your IDE has annotation processing enabled.

---

## 13. Costs, Risks and Alternatives

| Concern | Detail |
|---|---|
| **Speed** | a reflective call is slower than a direct call (it checks access and arguments, boxes primitives), though the JIT reduces the cost and caching the `Method`/`Field` objects matters more than the call itself. Never look up members inside a hot loop. |
| **No compile-time safety** | names are strings: a typo or a renamed method is found only when the code runs |
| **Encapsulation** | `setAccessible(true)` reads and writes private state; the module system now limits this for JDK internals |
| **Security** | reflection on untrusted class names (`Class.forName(userInput)`) can load dangerous classes: never instantiate a class named by user input; use an allow-list. Insecure deserialization attacks rely on it. |
| **Obfuscation / native images** | tools that remove "unused" code or compile ahead of time (GraalVM native image) need reflection configuration, because they cannot see which members you will look up by name |
| **Refactoring** | IDE renames do not update strings |

Alternatives when you can avoid reflection:

- **Method handles** (`java.lang.invoke.MethodHandle`): a typed, faster-after-warm-up way to call a method found by name; used in lambdas (`invokedynamic`) and many modern libraries.
- **`VarHandle`**: typed field access with memory-ordering control.
- **Code generation at build time** (annotation processors): the fast, safe route.
- Plain **interfaces and lambdas** (Strategy pattern): often all you needed.

---

## 14. Common Mistakes

| Mistake | Why | Fix |
|---|---|---|
| Custom annotation not found at run time | default retention is `CLASS` | `@Retention(RetentionPolicy.RUNTIME)` |
| Annotation allowed in the wrong place | no `@Target`, so it is accepted on anything | state `@Target` |
| `getFields()` finds nothing | it only returns **public** fields | `getDeclaredFields()` plus `setAccessible(true)` |
| `getDeclaredFields()` misses a parent's fields | it does not include inherited members | walk `getSuperclass()` |
| `NoSuchMethodException` for `getMethod("f", Integer.class)` | the method takes `int` | use `int.class` |
| Treating `InvocationTargetException` as the real error | the cause is inside | `e.getCause()` |
| `Class.forName(userInput).newInstance()` | arbitrary class loading | allow-list |
| Looking up `Method` objects on every call | slow | cache them |
| Expecting `list.getClass()` to show `List<String>` | erasure | read generic info from a field or method declaration |
| `setAccessible` on JDK classes in Java 17+ | `InaccessibleObjectException` | do not; use public APIs, or `--add-opens` as a last resort |
| Using `@Inherited` on a method or interface annotation | only works for classes | repeat it, or look it up with Spring's utilities |
| Expecting plain reflection to see meta-annotations | it does not | `AnnotatedElementUtils` |
| Reflection in new business code | fragile, slow, untestable | normal polymorphism |

---

## 15. Output Prediction Puzzles

**Puzzle 1: `getFields` vs `getDeclaredFields`**

```java
class Base { public int a; private int b; }
class Child extends Base { public int c; private int d; }
System.out.println(Child.class.getFields().length);
System.out.println(Child.class.getDeclaredFields().length);
```

**Puzzle 2: the runtime class**

```java
Object o = new ArrayList<String>();
List<String> list = new LinkedList<>();
System.out.println(o.getClass().getSimpleName() + " " + list.getClass().getSimpleName());
System.out.println(new ArrayList<String>().getClass() == new ArrayList<Integer>().getClass());
```

**Puzzle 3: retention**

```java
@Retention(RetentionPolicy.CLASS) @interface Hidden { }
@Retention(RetentionPolicy.RUNTIME) @interface Visible { }
@Hidden @Visible class Tagged { }
System.out.println(Tagged.class.getAnnotations().length);
System.out.println(Tagged.class.isAnnotationPresent(Hidden.class));
```

**Puzzle 4: `invoke` and exceptions**

```java
static void boom() { throw new IllegalStateException("inside"); }
try {
    Demo.class.getDeclaredMethod("boom").invoke(null);
} catch (Exception e) {
    System.out.println(e.getClass().getSimpleName());
    System.out.println(e.getCause().getMessage());
}
```

**Puzzle 5: wrong parameter type**

```java
try {
    String.class.getMethod("charAt", Integer.class);
} catch (NoSuchMethodException e) {
    System.out.println("no such method");
}
System.out.println(String.class.getMethod("charAt", int.class).invoke("hello", 1));
```

**Puzzle 6: private access**

```java
class Vault { private String secret = "gold"; }
Field f = Vault.class.getDeclaredField("secret");
try { f.get(new Vault()); } catch (IllegalAccessException e) { System.out.println("denied"); }
f.setAccessible(true);
System.out.println(f.get(new Vault()));
```

**Puzzle 7: generic type at run time**

```java
class Holder { public List<String> names; }
Type t = Holder.class.getField("names").getGenericType();
System.out.println(t);
System.out.println(Holder.class.getField("names").getType());
```

**Puzzle 8: annotation defaults**

```java
@Retention(RetentionPolicy.RUNTIME) @interface Config { int retries() default 3; String name(); }
@Config(name = "x") class A { }
@Config(name = "y", retries = 7) class B { }
System.out.println(A.class.getAnnotation(Config.class).retries() + " " + B.class.getAnnotation(Config.class).retries());
```

**Answers**

1. **`2`**, **`2`**. `getFields()` returns the **public** fields including inherited ones (`a` and `c`); `getDeclaredFields()` returns all fields declared in `Child` only (`c` and `d`).
2. **`ArrayList LinkedList`** and **`true`**. `getClass()` is the real runtime class, and generic type arguments are erased, so both lists are the same `ArrayList` class.
3. **`1`**, **`false`**. `CLASS` retention is stored in the class file but not available at run time, so only `@Visible` is seen.
4. **`InvocationTargetException`**, then **`inside`**. The method's own exception is wrapped; `getCause()` gives it.
5. **`no such method`**, then **`e`**. `charAt` takes a primitive `int`, so `Integer.class` finds nothing; the correct lookup works (`"hello".charAt(1)`).
6. **`denied`**, then **`gold`**. The access check fails until `setAccessible(true)` is called. (Run with `Vault` as a **separate top-level class**. Classes nested inside the same outer class are nestmates and can read each other's private members even without `setAccessible`, so inside one file with nested classes the first `get` would succeed.)
7. **`java.util.List<java.lang.String>`** and **`interface java.util.List`**. The generic type keeps its argument on the field declaration; the raw type does not.
8. **`3 7`**. The default `3` applies to `A`; `B` overrides it.

---

## 16. Interview Questions

**1. What is reflection, and where is it used?**
The ability to inspect and manipulate classes, fields, methods and constructors at run time. Used by Spring (DI, scanning), Hibernate, Jackson, JUnit and Mockito.

**2. How do you get a `Class` object?**
`Type.class`, `obj.getClass()`, or `Class.forName("name")`.

**3. `getMethods()` vs `getDeclaredMethods()`?**
`getMethods()` returns public methods including inherited ones; `getDeclaredMethods()` returns all methods declared by that class (any visibility), not inherited ones.

**4. How can reflection call a private method, and is that a problem?**
`getDeclaredMethod` then `setAccessible(true)`. It breaks encapsulation, so `private` is not a security boundary; the module system now restricts it for JDK internals.

**5. What is the `InvocationTargetException`?**
A wrapper thrown by `Method.invoke` around an exception thrown by the invoked method; read `getCause()`.

**6. What are annotations, and how are they processed?**
Metadata on declarations, read at compile time (annotation processors, the compiler), class-load time (tools) or run time (reflection), depending on retention.

**7. What are the retention policies?**
`SOURCE` (discarded by the compiler), `CLASS` (in the class file, not at run time; the default), `RUNTIME` (available to reflection).

**8. How do you create a custom annotation?**
`@interface` with elements, plus `@Retention(RUNTIME)` and `@Target`; then read it with `getAnnotation`.

**9. How does Spring's `@Autowired` work?**
The container scans for beans, finds injection points (constructors, fields, setters) by reflection, resolves the dependency by type (and qualifier), creates it recursively and injects it.

**10. How does Bean Validation work?**
A validator reflects over an object's fields, reads constraint annotations such as `@NotBlank`, checks each value and collects violations.

**11. Why does generic type information disappear at run time, and how can reflection still read some of it?**
Erasure removes it from instances; but field, method and class **declarations** keep their generic signatures, readable through `getGenericType()` and `ParameterizedType`, which is how `TypeReference` works.

**12. What is the performance cost of reflection?**
Slower than direct calls (access checks, boxing, no inlining at first) though the JIT narrows the gap; cache `Method`/`Field` objects and avoid lookups in hot paths.

**13. What is the difference between reflection and annotation processing?**
Reflection works at run time on loaded classes; annotation processing works at compile time on source and generates code, so it has no run-time cost (MapStruct, Dagger).

**14. Can reflection break a singleton?**
Yes, by calling a private constructor with `setAccessible(true)`; an enum singleton cannot be created reflectively.

**15. What changed for reflection in Java 17?**
Strong encapsulation of JDK internals: `setAccessible` on non-open JDK members throws `InaccessibleObjectException` (use `--add-opens` only as a stopgap).

---

## 17. Cheat Sheet

| Topic | Remember |
|---|---|
| Get a class | `X.class`, `obj.getClass()`, `Class.forName("x.Y")` |
| Members | `getFields()` public + inherited; `getDeclaredFields()` all, this class only |
| Create | `getDeclaredConstructor(types...).newInstance(args...)` |
| Call | `getMethod("name", int.class).invoke(obj, args)` (exact primitive types; `null` for static) |
| Errors | `InvocationTargetException.getCause()` |
| Private | `setAccessible(true)`; blocked for JDK internals since 17 |
| Generics | declarations keep them (`getGenericType`); instances do not |
| Annotation | `@Retention(RUNTIME)`, `@Target(...)`; read with `getAnnotation`, `isAnnotationPresent` |
| Retention | SOURCE (compiler), CLASS (default), RUNTIME (reflection) |
| Elements | primitives, `String`, `Class`, enum, annotation, arrays; constants only |
| Spring | scanning + reflection + proxies; meta-annotations via `AnnotatedElementUtils` |
| Compile-time | annotation processors generate code (MapStruct, Dagger) |
| Safety | never `Class.forName(userInput)`; cache reflective objects |

---

## 18. Practice Tasks

1. **Object printer.** Write `describe(Object o)` that prints the class name, then each field as `name = value` (including private and inherited ones), without calling `toString`.
2. **Mini JSON.** Write `toJson(Object o)` for objects whose fields are strings, numbers, booleans, lists and nested objects. Add `@JsonIgnore` and `@JsonName("x")` annotations and honour them.
3. **Test runner.** Write `@MyTest` and a runner that finds all methods carrying it in a class, runs each on a fresh instance, and prints PASS or FAIL(`cause`) using `InvocationTargetException.getCause()`. Add `@BeforeEach` support.
4. **Validator extension.** Add `@Pattern(regex)` and `@Email` to the tiny validator, and make it descend into fields that are themselves objects.
5. **Container extension.** Teach `MiniContainer` about `@Autowired` fields, a `@PostConstruct` method and an `@Scope("prototype")` annotation. Detect a circular dependency and report it clearly instead of overflowing the stack.
6. **Copy by reflection.** Write `copyProperties(source, target)` that copies fields with the same name and compatible type, skipping fields annotated `@NoCopy`. Compare with how MapStruct does it at compile time.
7. **Plugin loader.** Read class names from a text file, load each with `Class.forName`, check it implements `Plugin`, and call it. Add an allow-list of package prefixes and a test showing that a class outside it is rejected.
8. **Find it in Spring.** In a Spring Boot project of yours, write a test that uses `ApplicationContext.getBeansWithAnnotation(Service.class)` and prints each bean's class, then show that for a `@Transactional` bean `AopUtils.isAopProxy` is true and the class name contains `$$SpringCGLIB$$`.

*End of notes.*
