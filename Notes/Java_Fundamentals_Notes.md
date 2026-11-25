# Java Fundamentals: Detailed Notes

The foundation everything else builds on: how a program is laid out, the data types, converting between them, every operator, numbers that behave in surprising ways (overflow, floating point), control flow, arrays, methods, wrapper classes, console input and output, and `Math`. Each topic has the what, the why, a worked example and the usual traps.

> Examples target **Java 17 and 21**. Where a newer release changes something, a note says so. All code in these notes was compiled and run.

---

## Table of Contents

1. [How a Java Program Is Laid Out](#1-how-a-java-program-is-laid-out)
2. [Variables and Primitive Data Types](#2-variables-and-primitive-data-types)
3. [Literals, `var` and `final`](#3-literals-var-and-final)
4. [Type Conversion and Casting](#4-type-conversion-and-casting)
5. [Operators](#5-operators)
6. [Integer Overflow and Floating-Point Numbers](#6-integer-overflow-and-floating-point-numbers)
7. [Control Flow: Decisions](#7-control-flow-decisions)
8. [Control Flow: Loops](#8-control-flow-loops)
9. [Arrays](#9-arrays)
10. [Methods](#10-methods)
11. [Scope, Initialization and Default Values](#11-scope-initialization-and-default-values)
12. [Packages, Imports and Access Modifiers](#12-packages-imports-and-access-modifiers)
13. [Wrapper Classes and Autoboxing](#13-wrapper-classes-and-autoboxing)
14. [Console Input and Output](#14-console-input-and-output)
15. [The Math Class and Random Numbers](#15-the-math-class-and-random-numbers)
16. [Common Mistakes](#16-common-mistakes)
17. [Output Prediction Puzzles](#17-output-prediction-puzzles)
18. [Interview Questions](#18-interview-questions)
19. [Cheat Sheet](#19-cheat-sheet)
20. [Practice Tasks with Solutions](#20-practice-tasks-with-solutions)

---

## 1. How a Java Program Is Laid Out

```java
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello, world");
    }
}
```

| Part | Meaning |
|---|---|
| `public class Hello` | A **class**. A public class must live in a file with the same name, `Hello.java`. |
| `public static void main(String[] args)` | The **entry point**. The JVM calls it to start the program. |
| `System.out.println(...)` | Prints a line to standard output. |
| `{ }` | Braces group statements into blocks. |
| `;` | Ends a statement. |

**Compile and run:**

```
javac Hello.java      # produces Hello.class (bytecode)
java Hello            # the JVM runs it
java Hello.java       # Java 11+: compile and run a single file in one step
```

**Rules that trip beginners**

- Java is **case-sensitive**: `Main`, `main` and `MAIN` are three different names.
- Names (identifiers) start with a letter, `_` or `$`, then letters, digits, `_`, `$`. They cannot be keywords (`class`, `int`, `for`...).
- Convention: classes `PascalCase`, methods and variables `camelCase`, constants `UPPER_SNAKE_CASE`, packages `lowercase`.
- Comments: `// line`, `/* block */`, `/** documentation */`.

> **Newer Java (25):** a beginner-friendly form is final: a file can contain just `void main() { IO.println("Hello"); }` with no class and no `public static`. It is covered in the notes on what is new after Java 21. Everything below uses the classic form, which works in every version.

---

## 2. Variables and Primitive Data Types

A **variable** is a named box in memory that holds a value of one **type**. Java is **statically typed**: the type is fixed when you declare the variable and checked by the compiler.

```java
int age = 21;
double price = 99.5;
boolean active = true;
char grade = 'A';
String name = "Asha";     // String is a class, not a primitive
```

### The eight primitive types

| Type | Size | Range / values | Default | Typical use |
|---|---|---|---|---|
| `byte` | 8 bits | -128 to 127 | `0` | raw data, file bytes |
| `short` | 16 bits | -32,768 to 32,767 | `0` | rarely used |
| `int` | 32 bits | -2,147,483,648 to 2,147,483,647 (about ±2.1 billion) | `0` | **default for whole numbers** |
| `long` | 64 bits | about ±9.2 × 10^18 | `0L` | big counts, timestamps, IDs |
| `float` | 32 bits | about 7 decimal digits of precision | `0.0f` | rarely used |
| `double` | 64 bits | about 15 to 16 decimal digits of precision | `0.0` | **default for decimals** |
| `char` | 16 bits | `'\u0000'` to `'￿'` (0 to 65,535), unsigned | `'\u0000'` | one UTF-16 code unit |
| `boolean` | JVM-specific | `true` or `false` only | `false` | conditions |

- The sizes of the integer types are **fixed by the language**, so the same on every machine. That is why Java programs are portable.
- You can read the limits from the wrapper classes: `Integer.MAX_VALUE`, `Long.MIN_VALUE`, `Double.MAX_VALUE`.
- `boolean` is not a number. `if (1)` is a compile error, unlike C.

### Primitive vs reference types

| | Primitive | Reference |
|---|---|---|
| Holds | the value itself | an **address** of an object |
| Examples | `int`, `double`, `boolean` | `String`, arrays, `List`, your own classes |
| Can be `null` | No | Yes |
| Compare with | `==` | `equals()` for content, `==` for identity |
| Default (fields) | `0`, `false` | `null` |

```java
int a = 5;
int b = a;        // b gets its own copy of the value
b = 10;           // a is still 5

int[] x = {1, 2};
int[] y = x;      // y points to the SAME array
y[0] = 99;        // x[0] is now 99 too
```

### `char` is a number in disguise

```java
char c = 'A';
System.out.println(c + 1);          // 66   (char promoted to int)
System.out.println((char) (c + 1)); // B
System.out.println((int) 'a');      // 97
char d = 65;                        // legal: the constant 65 fits in a char
```

`'A'` to `'Z'` are 65 to 90, `'a'` to `'z'` are 97 to 122, `'0'` to `'9'` are 48 to 57. So `c - '0'` turns a digit character into its number, and `(char) (c + 32)` lower-cases an ASCII capital.

### Choosing a type

- Whole numbers: **`int`**. Use `long` when values can exceed about 2 billion (money in paise, file sizes, timestamps in milliseconds).
- Decimals: **`double`**. For **money**, use `BigDecimal` or whole units (paise) in a `long`, never `double` (section 6).
- Text: `String`. A single character: `char`. Yes/no: `boolean`.

---

## 3. Literals, `var` and `final`

### Literals (values written in code)

| Kind | Examples | Notes |
|---|---|---|
| Integer | `42`, `-7` | `int` unless suffixed |
| Long | `42L` | **Use capital `L`**; lowercase `l` looks like `1` |
| Decimal | `3.14`, `1e3` | `double` unless suffixed |
| Float | `3.14f` | needs the `f` |
| Hexadecimal | `0xFF` (255) | |
| Binary | `0b1010` (10) | Java 7+ |
| Octal | `017` (15) | a leading `0` means octal. `010` is 8, not 10! |
| Underscores | `1_000_000`, `0b1010_0001` | readability only |
| Char | `'a'`, `'\n'`, `'A'` | escapes: `\n \t \\ \' \"` |
| Text | `"hi"`, `"""text block"""` | |

```java
long big = 3_000_000_000L;     // without L this does not compile: the literal is too big for int
float f = 3.14f;               // float f = 3.14; would not compile (double to float loses data)
```

### `var` (Java 10+): local type inference

```java
var count = 10;                  // int
var names = new ArrayList<String>();   // ArrayList<String>
```

`var` is **not** dynamic typing: the type is still fixed, the compiler just works it out from the right side. It only works for **local variables with an initializer**: not fields, parameters, or `var x;`, and not `var x = null;`.

### `final`: a variable that can be assigned once

```java
final double GST = 0.18;      // a constant
GST = 0.28;                   // compile error
```

- A `final` **reference** cannot be re-pointed, but the object it points to can still change (`final List<String> xs = new ArrayList<>(); xs.add("ok");`).
- Constants shared by a class are usually `public static final`.

---

## 4. Type Conversion and Casting

### Widening (automatic, safe)

A smaller type fits into a larger one, so Java converts silently:

```
byte -> short -> int -> long -> float -> double
              char -> int
```

```java
int i = 100;
long l = i;          // fine
double d = l;        // fine
```

`long` to `float` is allowed even though a `long` has more significant digits than a `float`, so **precision can be lost** silently.

### Narrowing (manual, may lose data): a cast

```java
double price = 99.99;
int whole = (int) price;      // 99  (the fraction is cut off, NOT rounded)
long big = 3_000_000_000L;
int broken = (int) big;       // -1294967296  (the high bits are dropped)
```

- Casting a decimal to an integer **truncates toward zero**: `(int) -2.9` is `-2`.
- Casting an out-of-range number gives a wrong value **with no error**.
- Casting a `double` that is `NaN` to `int` gives `0`; a too-large `double` becomes `Integer.MAX_VALUE`.

### Promotion inside expressions

Before an arithmetic operator works, `byte`, `short` and `char` are promoted to `int`. If either operand is `long`, `float` or `double`, the result takes the **wider** type.

```java
byte a = 10, b = 20;
// byte c = a + b;          // compile error: a + b is an int
byte c = (byte) (a + b);    // OK with a cast
int  d = a + b;             // OK

System.out.println(5 / 2);        // 2    int / int  -> int
System.out.println(5 / 2.0);      // 2.5  int / double -> double
System.out.println('a' + 1);      // 98   char + int -> int
```

### Compound assignment hides a cast

```java
byte b = 10;
b += 5;          // fine: means b = (byte) (b + 5)
b++;             // fine too
// b = b + 5;    // compile error without a cast
```

```java
int x = 10;
x += 3.7;        // x = (int) (x + 3.7) = (int) 13.7 = 13   (no error, fraction lost)
```

### Converting to and from text

| Goal | Code |
|---|---|
| number to String | `String.valueOf(42)`, `Integer.toString(42)`, `"" + 42` |
| String to int | `Integer.parseInt("42")` (throws `NumberFormatException` if not a number) |
| String to double | `Double.parseDouble("3.5")` |
| String to boolean | `Boolean.parseBoolean("true")` (anything else gives `false`) |
| char to its digit | `Character.getNumericValue('7')` or `'7' - '0'` |

---

## 5. Operators

### Arithmetic

| Op | Meaning | Example |
|---|---|---|
| `+ - *` | add, subtract, multiply | |
| `/` | divide: **integer division** if both sides are integers | `7 / 2` is `3` |
| `%` | remainder | `7 % 2` is `1`; `-7 % 3` is `-1` |

- Integer division **truncates toward zero** (`-7 / 2` is `-3`) and by zero throws `ArithmeticException`. Floating-point division by zero gives `Infinity` or `NaN` instead.
- The sign of `%` follows the **left** operand. For a result that is never negative, use `Math.floorMod(-7, 3)`, which is `2`.
- `+` with a `String` is **concatenation**, evaluated left to right: `1 + 2 + "3"` is `"33"`, but `"1" + 2 + 3` is `"123"`.

### Increment and decrement

```java
int i = 5;
int a = i++;     // a = 5, then i = 6   (post: use, then change)
int b = ++i;     // i = 7, then b = 7   (pre: change, then use)
```

Avoid using `i++` twice in one expression: `i = i++ + ++i` is legal and unreadable.

### Relational and equality

`<  >  <=  >=  ==  !=` give a `boolean`. `==` on **references** compares identity, not content (section 13 and the String notes).

### Logical

| Op | Meaning | Short-circuits? |
|---|---|---|
| `&&` | AND | **Yes**: if the left is `false`, the right is not evaluated |
| `\|\|` | OR | **Yes**: if the left is `true`, the right is not evaluated |
| `!` | NOT | |
| `&`, `\|`, `^` | on booleans: AND, OR, XOR | **No**: both sides always run |

```java
String s = null;
if (s != null && s.length() > 3) { }    // safe: length() is never called on null
if (s != null & s.length() > 3) { }     // NullPointerException: & evaluates both sides
```

### Bitwise and shift operators (on integers)

| Op | Meaning | Example (shown in 8 bits) |
|---|---|---|
| `&` | AND | `0b1100 & 0b1010` = `0b1000` |
| `\|` | OR | `0b1100 \| 0b1010` = `0b1110` |
| `^` | XOR (different bits give 1) | `0b1100 ^ 0b1010` = `0b0110` |
| `~` | NOT (flip all bits) | `~5` is `-6` |
| `<<` | shift left (multiply by 2^n) | `3 << 2` is `12` |
| `>>` | shift right, **keeps the sign** (divide by 2^n, rounding down) | `-8 >> 1` is `-4` |
| `>>>` | shift right, **fills with zeros** | `-8 >>> 28` is `15` |

Uses: flags and permissions (`mask & FLAG != 0`), checking odd/even (`n & 1`), fast powers of two, hashing. The shift distance is taken **modulo 32 for `int`** (modulo 64 for `long`), so `1 << 33` is `2`, not `0`.

### Ternary, assignment, `instanceof`

```java
String label = (score >= 40) ? "pass" : "fail";     // condition ? ifTrue : ifFalse

int x = 5;
x += 2;  x -= 1;  x *= 3;  x /= 2;  x %= 4;        // compound assignment
x <<= 1; x &= 0xF;                                   // also exist for bit operators

Object o = "hi";
if (o instanceof String s && s.length() > 1) { }     // pattern matching (Java 16+): no separate cast
```

### Precedence (high to low)

| Level | Operators |
|---|---|
| 1 | `()` `[]` `.` postfix `++ --` |
| 2 | prefix `++ --`, unary `+ - ! ~`, casts |
| 3 | `* / %` |
| 4 | `+ -` |
| 5 | `<< >> >>>` |
| 6 | `< <= > >= instanceof` |
| 7 | `== !=` |
| 8 | `&` then `^` then `\|` |
| 9 | `&&` then `\|\|` |
| 10 | `? :` |
| 11 | `= += -= ...` (right to left) |

Do not memorize it. **Use parentheses** whenever the order is not obvious: `(a & b) == 0`, not `a & b == 0` (which is `a & (b == 0)` and does not compile).

---

## 6. Integer Overflow and Floating-Point Numbers

### Integer overflow wraps around silently

```java
int max = Integer.MAX_VALUE;        // 2147483647
System.out.println(max + 1);        // -2147483648  (wraps to the minimum)
System.out.println(Math.abs(Integer.MIN_VALUE));   // -2147483648  (!)
```

Safe arithmetic that throws instead of wrapping:

```java
Math.addExact(max, 1);        // throws ArithmeticException: integer overflow
Math.multiplyExact(100000, 100000);   // throws
Math.toIntExact(3_000_000_000L);      // throws
```

A classic bug: `int total = quantity * pricePerUnit;` overflows for big values. Use `long` (`(long) quantity * pricePerUnit`) or the `Exact` methods. Note `long x = a * b` with two `int`s still multiplies as `int` first.

### Floating point is approximate

`float` and `double` store numbers in binary. Many decimals, such as `0.1`, have **no exact binary form**, like 1/3 has no exact decimal form.

```java
System.out.println(0.1 + 0.2);          // 0.30000000000000004
System.out.println(0.1 + 0.2 == 0.3);   // false
System.out.println(1.0 / 0);            // Infinity
System.out.println(0.0 / 0);            // NaN
System.out.println(Double.NaN == Double.NaN);   // false: NaN is not equal to anything, even itself
```

**Comparing doubles:** never with `==`. Compare with a tolerance:

```java
static boolean close(double a, double b) {
    return Math.abs(a - b) < 1e-9;
}
```

Use `Double.isNaN(x)` to test for `NaN`, `Double.compare(a, b)` to sort.

`float` has only about 7 digits: `16777216f + 1` is still `16777216`. Use `double` unless memory demands otherwise.

### Money: `BigDecimal`

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

BigDecimal price = new BigDecimal("19.99");     // build from a STRING
BigDecimal qty   = BigDecimal.valueOf(3);
BigDecimal total = price.multiply(qty);                         // 59.97
BigDecimal gst   = total.multiply(new BigDecimal("0.18"))
                        .setScale(2, RoundingMode.HALF_UP);     // 10.79
System.out.println(total.add(gst));                             // 70.76
```

| Rule | Why |
|---|---|
| `new BigDecimal("0.1")` or `BigDecimal.valueOf(0.1)`, **never** `new BigDecimal(0.1)` | the `double` constructor copies the binary error: `0.1000000000000000055511151231257827...` |
| Compare with `compareTo`, **not** `equals` | `new BigDecimal("2.0").equals(new BigDecimal("2.00"))` is `false` (different scale); `compareTo` gives `0` |
| `divide` needs a scale or `MathContext` | `1/3` has no finite decimal form, so `a.divide(b)` throws `ArithmeticException` |
| Immutable | every operation returns a **new** object; ignoring the result does nothing |

`BigInteger` is the same idea for whole numbers of any size (`new BigInteger("123456789012345678901234567890")`, `.add`, `.multiply`, `.pow`, `.mod`). Factorials and Fibonacci numbers beyond 20 or 90 need it.

---

## 7. Control Flow: Decisions

### `if` / `else if` / `else`

```java
if (marks >= 90)      grade = 'A';
else if (marks >= 75) grade = 'B';
else if (marks >= 40) grade = 'C';
else                  grade = 'F';
```

- The condition must be a `boolean`. Always use braces: the "dangling else" bug and edits that add a line below an unbraced `if` are classic.
- `if (x = 5)` does not compile (an assignment is not a boolean), but `if (flag = true)` for a `boolean flag` **does** compile and always runs. Write `if (flag)`.

### Classic `switch` (statement) and fall-through

```java
switch (day) {
    case 1:
    case 7:
        System.out.println("weekend");
        break;                      // without break, execution FALLS THROUGH into the next case
    case 2:
        System.out.println("Monday");
        break;
    default:
        System.out.println("weekday");
}
```

Allowed selector types: `byte`, `short`, `char`, `int` (and their wrappers), `String`, `enum`. Not `long`, `float`, `double`, `boolean`. Case labels must be **constants** and unique.

### Modern `switch` (Java 14+): arrows and expressions

```java
String type = switch (day) {
    case 1, 7 -> "weekend";             // no fall-through, no break
    case 2, 3, 4, 5, 6 -> "weekday";
    default -> throw new IllegalArgumentException("bad day: " + day);
};

int length = switch (word) {
    case "one" -> 1;
    case "three" -> {
        int n = 3;
        yield n;                        // yield returns a value from a block
    }
    default -> word.length();
};
```

- A `switch` **expression** must be **exhaustive** (cover every value, usually with `default`; for an enum or sealed type, covering all constants is enough) and every branch yields a value.
- Pattern matching in `switch` (type patterns, `when` guards, `case null`) is in the Modern Language Features notes.

---

## 8. Control Flow: Loops

```java
for (int i = 0; i < 5; i++) { }          // when you know the count
while (condition) { }                    // check first, maybe never runs
do { } while (condition);                // runs at least once
for (String s : names) { }               // enhanced for: arrays and Iterables
```

### `for` in detail

```java
for (init; condition; update) { body }
//  1. init runs once   2. condition checked   3. body   4. update   5. back to 2
```

All three parts are optional: `for (;;) { }` is an infinite loop.

### Enhanced `for`

```java
int[] scores = {80, 90, 70};
int sum = 0;
for (int s : scores) sum += s;
```

You get each element but **not the index**, and you cannot change the collection while looping (the `ConcurrentModificationException` in the Collections notes). Assigning to the loop variable does not change the array.

### `break`, `continue`, labels

```java
for (int i = 0; i < 10; i++) {
    if (i == 3) continue;       // skip the rest of this iteration
    if (i == 6) break;          // leave the loop
    System.out.print(i + " ");  // 0 1 2 4 5
}

outer:
for (int i = 0; i < 3; i++) {
    for (int j = 0; j < 3; j++) {
        if (i * j == 2) break outer;     // leaves BOTH loops
    }
}
```

### Common loop bugs

| Bug | Example | Fix |
|---|---|---|
| Off by one | `i <= arr.length` throws `ArrayIndexOutOfBoundsException` | `i < arr.length` |
| Infinite loop | forgetting `i++` in a `while` | check the update |
| Modifying the collection while iterating | `for (x : list) list.remove(x)` | `Iterator.remove()` or `removeIf` |
| `float`/`double` counters | `for (double d = 0; d != 1; d += 0.1)` never hits exactly 1 | use an `int` counter |
| Stray semicolon | `for (...);  { }` the body is empty | remove it |

---

## 9. Arrays

An **array** is a fixed-size, ordered block of elements of one type, with a zero-based index. It is an **object**, even for primitives.

```java
int[] a = new int[5];                 // five zeros
int[] b = {10, 20, 30};               // literal
int[] c = new int[]{1, 2, 3};
String[] names = new String[3];       // three nulls

System.out.println(b.length);         // 3  (a field, not a method)
b[0] = 99;
// b[3] -> ArrayIndexOutOfBoundsException
```

- **Default values:** `0`, `0.0`, `false`, `'\u0000'` for primitives; `null` for objects.
- **The size is fixed** at creation. For a growing list use `ArrayList`.
- `int a[]` also compiles (C style). Prefer `int[] a`.
- Arrays are **reference types**: `int[] y = x;` is not a copy; use `Arrays.copyOf` or `.clone()`.
- Arrays of objects hold **references**; a `new Student[3]` has no students yet, only three `null`s.

### Two-dimensional arrays

```java
int[][] grid = new int[3][4];                 // 3 rows, 4 columns
int[][] m = { {1, 2}, {3, 4}, {5, 6} };
System.out.println(m[2][1]);                  // 6
System.out.println(m.length);                 // 3 (rows)
System.out.println(m[0].length);              // 2 (columns in row 0)

int[][] jagged = new int[3][];                // rows can have different lengths
jagged[0] = new int[]{1};
jagged[1] = new int[]{1, 2};
jagged[2] = new int[]{1, 2, 3};
```

### The `Arrays` utility class

| Method | What it does |
|---|---|
| `Arrays.toString(a)` | `[10, 20, 30]` (printing an array directly shows an address like `[I@6d06d69c`) |
| `Arrays.deepToString(m)` | text for nested arrays |
| `Arrays.sort(a)` | sorts in place (dual-pivot quicksort for primitives, TimSort for objects) |
| `Arrays.sort(a, from, to)` | sorts a range |
| `Arrays.binarySearch(a, key)` | needs a **sorted** array; returns the index or `-(insertionPoint) - 1` |
| `Arrays.fill(a, v)` | sets every element |
| `Arrays.copyOf(a, n)` | new array of length `n` (pads with zeros or truncates) |
| `Arrays.copyOfRange(a, from, to)` | a slice, `to` exclusive |
| `Arrays.equals(a, b)`, `Arrays.deepEquals` | content comparison (`a.equals(b)` is identity!) |
| `Arrays.asList(arr)` | a fixed-size `List` view (not for primitive arrays) |
| `Arrays.stream(a)` | a stream; `Arrays.stream(a).sum()` |

```java
int[] a = {5, 3, 9, 1};
Arrays.sort(a);
System.out.println(Arrays.toString(a));                    // [1, 3, 5, 9]
System.out.println(Arrays.binarySearch(a, 5));             // 2
System.out.println(Arrays.binarySearch(a, 4));             // -3  (would be inserted at index 2)
int[] bigger = Arrays.copyOf(a, 6);                        // [1, 3, 5, 9, 0, 0]
```

### Array covariance trap

```java
Object[] objs = new String[2];
objs[0] = 42;        // compiles, but throws ArrayStoreException at run time
```

Arrays remember their real element type at run time. Generics (`List<Object>` is not a `List<String>`) fix this at compile time, one reason to prefer collections.

---

## 10. Methods

A **method** is a named, reusable block of code with optional parameters and a return type.

```java
static int add(int a, int b) {     // modifiers  returnType  name(parameters)
    return a + b;
}
int sum = add(3, 4);               // 7
```

- `void` means "returns nothing". Every non-`void` path must `return` a value.
- `return` ends the method immediately.

### Java is always pass-by-value

The method gets a **copy** of each argument. For a primitive, that is a copy of the value. For an object, it is a copy of the **reference**, so the method can change the object's contents but cannot make the caller's variable point elsewhere.

```java
static void change(int n, int[] arr, StringBuilder sb) {
    n = 99;                  // changes only the local copy
    arr[0] = 99;             // changes the shared array
    sb.append("!");          // changes the shared object
    sb = new StringBuilder("new");   // re-points only the local copy
}
```

After `int n = 1; int[] arr = {1}; StringBuilder sb = new StringBuilder("hi"); change(n, arr, sb);` the values are `n == 1`, `arr[0] == 99`, `sb` is `hi!`.

### Overloading

Several methods with the **same name and different parameter lists** (count, types or order; the return type alone does not count).

```java
static int    max(int a, int b)       { return a > b ? a : b; }
static double max(double a, double b) { return a > b ? a : b; }
static int    max(int a, int b, int c){ return max(max(a, b), c); }
```

The compiler chooses at **compile time** using, in order: exact match, widening, boxing, then varargs. Details and puzzles are in the OOP notes.

### Varargs

```java
static int sum(int... nums) {          // nums is an int[] inside the method
    int total = 0;
    for (int n : nums) total += n;
    return total;
}
sum();            // 0
sum(1, 2, 3);     // 6
sum(new int[]{4, 5});   // 9
```

The varargs parameter must be **last**, and there can be only one. It is the lowest priority in overload resolution.

### Recursion

A method that calls itself needs a **base case** and progress toward it.

```java
static long factorial(int n) {
    if (n <= 1) return 1;            // base case
    return n * factorial(n - 1);     // smaller problem
}
```

A missing base case ends in `StackOverflowError`. Java does not optimize tail calls, so a loop is safer for deep recursion (tens of thousands of levels).

### `main`'s arguments

```java
public static void main(String[] args) {
    // java App Asha 21   ->  args = {"Asha", "21"}; everything arrives as a String
    int age = Integer.parseInt(args[1]);
}
```

---

## 11. Scope, Initialization and Default Values

- **Local variables** (inside methods) have **no default value**. Reading one before assigning is a compile error.
- **Fields** (inside a class, outside methods) get defaults: `0`, `false`, `null`.
- A variable lives from its declaration to the end of its **block** `{ }`.
- A local variable cannot **shadow** another local in the same method:

```java
int x = 1;
{
    // int x = 2;          // compile error: x is already defined
}
for (int i = 0; i < 3; i++) { }
// i is gone here; you can declare another i
```

- A parameter or local may shadow a **field** (that is what `this.name = name` is for).
- Variables used inside a lambda or anonymous class must be **effectively final** (never reassigned after the first assignment).

```java
int count = 0;
Runnable r = () -> System.out.println(count);
// count++;               // would make the lambda above a compile error
```

---

## 12. Packages, Imports and Access Modifiers

A **package** groups related classes and avoids name clashes: `package com.company.app;` must be the first statement and match the folder path.

```java
import java.util.List;          // one class
import java.util.*;             // all classes in a package (not sub-packages)
import static java.lang.Math.*; // static members, so you can write sqrt(2)
```

`java.lang` (String, Math, Integer, System...) is imported automatically. If two imported classes share a name (`java.util.Date` and `java.sql.Date`), use the **fully qualified name** for one of them.

### Access modifiers

| Modifier | Same class | Same package | Subclass in another package | Everywhere |
|---|---|---|---|---|
| `private` | Yes | No | No | No |
| (none, "package-private") | Yes | Yes | No | No |
| `protected` | Yes | Yes | **Yes** | No |
| `public` | Yes | Yes | Yes | Yes |

Make things as **private as possible**; widen only when needed. A top-level class can only be `public` or package-private.

---

## 13. Wrapper Classes and Autoboxing

Each primitive has an object twin, needed by generics and collections (`List<int>` is illegal; `List<Integer>` works).

| Primitive | Wrapper |
|---|---|
| `byte short int long` | `Byte Short Integer Long` |
| `float double` | `Float Double` |
| `char boolean` | `Character Boolean` |

```java
Integer a = 42;            // autoboxing: int -> Integer
int b = a;                 // unboxing:   Integer -> int
Integer parsed = Integer.valueOf("123");
int n = Integer.parseInt("123");
```

### The traps

**1. The Integer cache.** `valueOf` (used by autoboxing) reuses objects for **-128 to 127**:

```java
Integer x = 127, y = 127;
System.out.println(x == y);        // true  (same cached object)
Integer p = 1000, q = 1000;
System.out.println(p == q);        // false (two different objects)
System.out.println(p.equals(q));   // true
```

Always compare wrappers with `equals` (or unbox them).

**2. Unboxing `null` throws.**

```java
Integer count = null;
int c = count;                     // NullPointerException
```

A `Map<String, Integer>` `get` that finds nothing returns `null`, so `int v = map.get("missing");` crashes.

**3. Mixed ternaries unbox.**

```java
Integer a = null;
int r = true ? a : 0;              // NullPointerException: the ternary unboxes a
```

**4. Performance.** Boxing in a hot loop creates objects: `Long sum = 0L; for (...) sum += i;` allocates a new `Long` every iteration. Use `long`.

### Useful wrapper methods

| Method | Example |
|---|---|
| `Integer.MAX_VALUE`, `MIN_VALUE` | limits |
| `Integer.compare(a, b)` | safe comparison (never overflows like `a - b`) |
| `Integer.toBinaryString(10)`, `toHexString`, `Integer.parseInt("ff", 16)` | bases |
| `Integer.bitCount(7)` | number of 1 bits (3) |
| `Character.isDigit / isLetter / isUpperCase / toUpperCase` | character tests |
| `Double.isNaN`, `Double.compare`, `Double.parseDouble` | doubles |

---

## 14. Console Input and Output

### Output

```java
System.out.println("line");       // adds a newline
System.out.print("no newline");
System.out.printf("%s is %d years old and owes %.2f%n", "Asha", 21, 1234.5);
System.err.println("errors go here");
```

| Format | Meaning | Example |
|---|---|---|
| `%s` | any value as text | `%10s` right-align in 10, `%-10s` left-align |
| `%d` | integer | `%05d` zero-pad to 5, `%,d` thousands: `1,234,567` |
| `%f` | decimal | `%.2f` two places |
| `%c`, `%b` | char, boolean | |
| `%n` | platform newline (prefer to `\n`) | |
| `%x`, `%o`, `%e` | hex, octal, scientific | |

`String.format(...)` builds the same text as a `String`; `"...".formatted(args)` (Java 15+) does too.

### Input with `Scanner`

```java
import java.util.Scanner;

Scanner in = new Scanner(System.in);
System.out.print("Name: ");
String name = in.nextLine();
System.out.print("Age: ");
int age = in.nextInt();
in.nextLine();                   // consume the leftover newline!
System.out.print("City: ");
String city = in.nextLine();
```

- **The newline trap:** `nextInt()` reads the number but leaves the Enter key in the buffer, so the next `nextLine()` returns an empty string. Call `in.nextLine()` once to discard it, or read everything with `nextLine()` and parse.
- `nextInt()` throws `InputMismatchException` for non-numbers; check `in.hasNextInt()` first.
- Closing a `Scanner` on `System.in` also closes `System.in`. Create one and keep it for the program.
- For fast input of large data, `BufferedReader` with `readLine()` is quicker.

```java
BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
String line = br.readLine();        // throws IOException; null at end of input
```

---

## 15. The Math Class and Random Numbers

All members of `Math` are static.

| Method | Result |
|---|---|
| `Math.abs(-5)` | `5` (but `Math.abs(Integer.MIN_VALUE)` is negative!) |
| `Math.max(a, b)`, `Math.min(a, b)` | larger, smaller |
| `Math.pow(2, 10)` | `1024.0` (a `double`) |
| `Math.sqrt(16)`, `Math.cbrt(27)` | `4.0`, `3.0` |
| `Math.floor(2.7)`, `Math.ceil(2.1)` | `2.0`, `3.0` |
| `Math.round(2.5)` | `3` (a `long`; halves round **up**: `Math.round(-2.5)` is `-2`) |
| `Math.floorDiv(-7, 2)`, `Math.floorMod(-7, 3)` | `-4`, `2` (rounding toward negative infinity) |
| `Math.hypot(3, 4)` | `5.0` |
| `Math.log`, `log10`, `exp`, `sin`, `cos`, `toRadians` | the usual |
| `Math.random()` | a `double` from 0.0 up to (not including) 1.0 |
| `Math.addExact` and friends | overflow-checked arithmetic |
| `Math.clamp(value, min, max)` | Java 21+: limits a value to a range |

### Random numbers

```java
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

Random r = new Random(42);                  // a seed makes the sequence repeatable (good for tests)
int dice = r.nextInt(6) + 1;                // 1..6
int between = ThreadLocalRandom.current().nextInt(10, 20);   // 10..19; best choice for multi-threaded code
```

`Random` and `Math.random()` are **not secure**. For tokens, passwords or anything an attacker must not guess, use `java.security.SecureRandom`.

---

## 16. Common Mistakes

| Mistake | Why it is wrong | Fix |
|---|---|---|
| `int x = 3 / 2;` expecting `1.5` | integer division | `3 / 2.0` or `(double) 3 / 2` |
| `(double) (a / b)` | the division already happened as integers | `(double) a / b` |
| `if (s == "yes")` | compares references, not text | `"yes".equals(s)` |
| `double` for money | `0.1 + 0.2 != 0.3` | `BigDecimal` or `long` paise |
| `a == b` for two `Integer`s | only cached from -128 to 127 | `a.equals(b)` |
| `i <= arr.length` | one past the end | `i < arr.length` |
| Octal literal `010` | it is 8 | no leading zeros |
| `1 / 0` vs `1.0 / 0` | exception vs `Infinity` | know which type you have |
| `long big = 60 * 60 * 24 * 365 * 1000;` | overflows as `int` before widening | `60L * 60 * ...` |
| Forgetting `break` in an old `switch` | fall-through | use `->` switch |
| `nextInt()` then `nextLine()` | empty line from the leftover newline | extra `nextLine()` |
| Comparing floats with `==` | binary rounding | tolerance, or `BigDecimal` |
| `char + int` printed as a number | promotion to `int` | cast to `char` |
| Using `&` instead of `&&` | no short-circuit, can throw | `&&` for conditions |
| Ignoring the return of `String`/`BigDecimal` methods | they are immutable | assign the result |

---

## 17. Output Prediction Puzzles

**Puzzle 1: integer division and promotion**

```java
System.out.println(7 / 2);
System.out.println(7 / 2.0);
System.out.println(-7 / 2);
System.out.println(-7 % 3);
System.out.println(Math.floorMod(-7, 3));
```

**Puzzle 2: `++` and `--`**

```java
int i = 5;
int a = i++;
int b = ++i;
System.out.println(a + " " + b + " " + i);
```

**Puzzle 3: overflow**

```java
int max = Integer.MAX_VALUE;
System.out.println(max + 1);
System.out.println((long) max + 1);
System.out.println(Math.abs(Integer.MIN_VALUE));
```

**Puzzle 4: floating point**

```java
System.out.println(0.1 + 0.2 == 0.3);
System.out.println(0.1f + 0.2f == 0.3f);
System.out.println(1.0 / 0);
System.out.println(0.0 / 0 == 0.0 / 0);
```

**Puzzle 5: casting**

```java
System.out.println((int) 3.99);
System.out.println((int) -3.99);
System.out.println((byte) 200);
System.out.println((char) 66);
System.out.println((int) 'A' + 1);
```

**Puzzle 6: compound assignment**

```java
byte b = 10;
b += 300;
System.out.println(b);
int x = 10;
x -= 2.5;
System.out.println(x);
```

**Puzzle 7: short-circuit**

```java
int n = 0;
boolean r1 = (n != 0) && (10 / n > 1);
boolean r2 = (n == 0) || (10 / n > 1);
System.out.println(r1 + " " + r2);
```

**Puzzle 8: shifts**

```java
System.out.println(1 << 3);
System.out.println(-16 >> 2);
System.out.println(-16 >>> 28);
System.out.println(1 << 33);
```

**Puzzle 9: switch fall-through**

```java
int v = 2;
switch (v) {
    case 1: System.out.print("one ");
    case 2: System.out.print("two ");
    case 3: System.out.print("three ");
    default: System.out.print("other ");
}
System.out.println();
```

**Puzzle 10: Integer cache**

```java
Integer a = 100, b = 100;
Integer c = 200, d = 200;
System.out.println(a == b);
System.out.println(c == d);
System.out.println(c.equals(d));
```

**Puzzle 11: ternary promotion**

```java
Object o = true ? 1 : 2.0;
System.out.println(o);
char ch = 'a';
System.out.println(true ? ch : 0);
System.out.println(false ? 1 : 'b');
```

**Puzzle 12: pass by value**

```java
int[] arr = {1, 2, 3};
int n = 5;
modify(arr, n);
System.out.println(Arrays.toString(arr) + " " + n);

static void modify(int[] a, int n) {
    a[0] = 100;
    a = new int[]{9, 9};
    n = 50;
}
```

**Puzzle 13: arrays**

```java
int[] a = {3, 1, 2};
int[] b = a;
int[] c = a.clone();
Arrays.sort(a);
System.out.println(Arrays.toString(b) + " " + Arrays.toString(c));
System.out.println(a == b);
System.out.println(Arrays.equals(a, c));
```

**Answers**

1. **`3`, `3.5`, `-3`, `-1`, `2`.** Integer division truncates toward zero, and `%` takes the sign of the left operand. `floorMod` is never negative for a positive divisor.
2. **`5 7 7`.** `i++` yields `5` then `i` is 6; `++i` makes `i` 7 and yields `7`.
3. **`-2147483648`**, **`2147483648`**, **`-2147483648`**. `MAX_VALUE + 1` wraps to the minimum; casting to `long` first avoids it; the absolute value of the minimum does not fit in an `int`.
4. **`false`**, **`true`**, **`Infinity`**, **`false`**. The `double` sum is off by a hair, but the `float` sum happens to round to exactly the `float` nearest `0.3` (a coincidence of rounding; never rely on it). Dividing a double by zero gives `Infinity`, and `NaN` is never equal to anything.
5. **`3`**, **`-3`**, **`-56`**, **`B`**, **`66`**. Casting truncates toward zero; `200` does not fit in a `byte` (200 - 256 = -56); `(int) 'A' + 1` is `65 + 1`.
6. **`54`** and **`7`**. `b += 300` means `b = (byte) (10 + 300)`, and `310 - 256 = 54`. `x -= 2.5` is `(int) (10 - 2.5)` = `(int) 7.5` = `7`.
7. **`false true`.** Both right-hand sides are skipped, so no division by zero happens.
8. **`8`**, **`-4`**, **`15`**, **`2`**. `>>` keeps the sign; `>>>` fills with zeros (`-16 >>> 28` is the top four bits, `1111`); the shift distance for an `int` is taken modulo 32, so `1 << 33` equals `1 << 1`.
9. **`two three other`.** Without `break`, execution falls through every following case, including `default`.
10. **`true`**, **`false`**, **`true`**. Values from -128 to 127 are cached; `200` is boxed into separate objects; `equals` compares values.
11. **`1.0`**, **`a`**, **`b`**. In `true ? 1 : 2.0` both branches are promoted to `double`, so `1` becomes `1.0`. In `true ? ch : 0` the `0` is a constant that fits in a `char`, so the whole expression is a `char` and `println` prints the character `a`, not `97`. In `false ? 1 : 'b'` the constant `1` also fits in a `char`, so the result is the `char` `b`. (If one operand were a non-constant `int` variable, the result would be an `int` and print `97`.)
12. **`[100, 2, 3] 5`.** The array contents change through the copied reference; re-pointing `a` and changing `n` affect only the method's copies.
13. **`[1, 2, 3] [3, 1, 2]`**, **`true`**, **`false`**. `b` is the same array, so it is sorted too; `c` is an independent copy with the old order; `Arrays.equals` compares contents.

---

## 18. Interview Questions

**1. What are the primitive types in Java, and why does Java have them?**
Eight: `byte short int long float double char boolean`. They store values directly (no object overhead), are fast, and have fixed sizes on every platform.

**2. Difference between `==` and `equals()`?**
`==` compares primitive values, or object **identity** (same object). `equals()` compares **content** when a class overrides it (`String`, wrappers, records).

**3. What is the difference between `int` and `Integer`?**
`int` is a primitive (cannot be `null`, faster). `Integer` is an object wrapper (can be `null`, usable in collections and generics). Conversion between them is automatic (autoboxing), which can throw `NullPointerException` on `null`.

**4. Is Java pass-by-value or pass-by-reference?**
Always **pass-by-value**. For objects, the value copied is the reference, so a method can modify the object but cannot re-point the caller's variable.

**5. What happens on integer overflow?**
It wraps around silently (`Integer.MAX_VALUE + 1` is `Integer.MIN_VALUE`). Use `long`, `Math.addExact`, or `BigInteger`.

**6. Why is `0.1 + 0.2 != 0.3`, and what should money use?**
Binary floating point cannot represent most decimal fractions exactly. Use `BigDecimal` (built from strings) or integer minor units (paise).

**7. Explain `&&` vs `&`.**
`&&` short-circuits (stops when the result is known). `&` on booleans evaluates both sides; on integers it is a bitwise AND.

**8. What is the difference between `>>` and `>>>`?**
`>>` shifts right keeping the sign bit (arithmetic shift). `>>>` shifts right filling with zeros (logical shift), so a negative number becomes a large positive one.

**9. What is the output of `System.out.println(1 + 2 + "3" + 4 + 5);`?**
`3345`. Left to right: `1 + 2` is `3`, then string concatenation takes over.

**10. How does `switch` differ from `if-else`, and what is new in modern `switch`?**
`switch` selects among constants (int types, `String`, `enum`). Modern `switch` has arrow cases (no fall-through), multiple labels per case, can be an **expression** returning a value, and supports pattern matching.

**11. What is the difference between `final`, `finally` and `finalize`?**
`final`: a constant variable, a method that cannot be overridden, or a class that cannot be extended. `finally`: a block that runs after `try`/`catch`. `finalize()`: a deprecated, unreliable hook that the garbage collector may call.

**12. What is autoboxing, and what are its pitfalls?**
Automatic conversion between primitives and wrappers. Pitfalls: `NullPointerException` on unboxing `null`, `==` surprises beyond the cache range, and hidden allocation in loops.

**13. What is the default value of an uninitialized local variable?**
There is none: a local variable must be assigned before use (compile error otherwise). Fields get `0`, `false` or `null`.

**14. Can you declare an array of size zero or negative?**
Size zero is legal (`new int[0]`). A negative size throws `NegativeArraySizeException` at run time.

**15. Why is `char` 16 bits and how do you handle emoji?**
`char` is a UTF-16 code unit. Characters beyond U+FFFF (many emoji) use two `char`s (a surrogate pair); use `String.codePoints()` and `Character.charCount` to handle them correctly.

**16. What is the difference between `Math.round`, `Math.floor` and a cast to `int`?**
`round` rounds half up and returns `long`/`int`; `floor` rounds toward negative infinity and returns `double`; a cast truncates toward zero.

---

## 19. Cheat Sheet

| Topic | Remember |
|---|---|
| Default types | whole `int`, decimal `double` |
| Sizes | byte 8, short 16, int 32, long 64, float 32, double 64, char 16 bits |
| Literals | `L` for long, `f` for float, `0x` hex, `0b` binary, leading `0` octal |
| Narrowing | needs a cast, truncates, may wrap |
| `byte + byte` | is an `int` |
| `x += y` | includes a cast back to the type of `x` |
| Integer division | truncates toward zero; `%` follows the sign of the left side |
| Overflow | wraps silently; `Math.xxxExact` throws |
| Doubles | never `==`; money uses `BigDecimal("...")` |
| `&&` `\|\|` | short-circuit; `&` `\|` do not |
| Shifts | `>>` keeps sign, `>>>` fills zeros, distance mod 32 for `int` |
| `switch` | old: break or fall through; new: `->`, expression, `yield` |
| Arrays | zero-based, fixed size, `length` field, `Arrays.toString` to print |
| Parameters | pass-by-value (references copied) |
| Wrapper cache | -128 to 127; compare with `equals` |
| `Scanner` | `nextInt()` leaves the newline behind |

---

## 20. Practice Tasks with Solutions

**Task 1.** Print the digits of an integer in reverse and their sum, without converting to a string.

```java
static void digits(int n) {
    int sum = 0;
    StringBuilder reversed = new StringBuilder();
    n = Math.abs(n);
    do {
        int d = n % 10;
        sum += d;
        reversed.append(d);
        n /= 10;
    } while (n > 0);
    System.out.println(reversed + " sum=" + sum);
}
// digits(1234) prints 4321 sum=10
```

**Task 2.** Is a number prime? (Check divisors up to the square root.)

```java
static boolean isPrime(int n) {
    if (n < 2) return false;
    for (int i = 2; (long) i * i <= n; i++) {
        if (n % i == 0) return false;
    }
    return true;
}
// isPrime(97) is true; isPrime(91) is false (7 * 13)
```

**Task 3.** Compute the n-th Fibonacci number with a loop, and show where `long` overflows.

```java
static long fib(int n) {
    if (n == 0) return 0;
    long prev = 0, cur = 1;
    for (int i = 2; i <= n; i++) {
        long next = Math.addExact(prev, cur);   // throws ArithmeticException when it no longer fits
        prev = cur;
        cur = next;
    }
    return cur;
}
// fib(10) is 55; fib(92) is 7540113804746346429 (the largest that fits in a long); fib(93) throws
```

**Task 4.** Find the largest and second-largest values in an array in one pass.

```java
static int[] top2(int[] a) {
    int first = Integer.MIN_VALUE, second = Integer.MIN_VALUE;
    for (int v : a) {
        if (v > first) { second = first; first = v; }
        else if (v > second && v != first) { second = v; }
    }
    return new int[]{first, second};
}
// top2(new int[]{5, 9, 2, 9, 7}) returns {9, 7}
```

**Task 5.** Rotate an array right by `k` places in place (reverse three times).

```java
static void rotate(int[] a, int k) {
    int n = a.length;
    k %= n;
    reverse(a, 0, n - 1);
    reverse(a, 0, k - 1);
    reverse(a, k, n - 1);
}
static void reverse(int[] a, int i, int j) {
    while (i < j) { int t = a[i]; a[i++] = a[j]; a[j--] = t; }
}
// rotate({1,2,3,4,5}, 2) gives {4,5,1,2,3}
```

**Task 6.** Transpose a matrix.

```java
static int[][] transpose(int[][] m) {
    int[][] t = new int[m[0].length][m.length];
    for (int i = 0; i < m.length; i++)
        for (int j = 0; j < m[0].length; j++)
            t[j][i] = m[i][j];
    return t;
}
```

**Task 7.** Convert a decimal number to binary by hand, and check it against `Integer.toBinaryString`.

```java
static String toBinary(int n) {
    if (n == 0) return "0";
    StringBuilder sb = new StringBuilder();
    while (n > 0) {
        sb.append(n & 1);     // lowest bit
        n >>= 1;
    }
    return sb.reverse().toString();
}
// toBinary(10) is "1010"
```

**Task 8.** Add two prices, `1.10` and `2.20`, exactly with `BigDecimal`, and show that `double` is off.

```java
BigDecimal total = new BigDecimal("1.10").add(new BigDecimal("2.20"));
System.out.println(total);                    // 3.30
double d = 1.10 + 2.20;
System.out.println(d);                        // 3.3000000000000003
```

*End of notes.*
