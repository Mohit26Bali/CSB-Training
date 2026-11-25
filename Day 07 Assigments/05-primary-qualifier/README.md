# 5. Multiple Beans: @Primary and @Qualifier

**Concept:** Spring Core. `PaymentService` has two implementations, `CardPaymentService` and `UpiPaymentService` (plus `WalletPaymentService` as the optional third). `CheckoutService` needs "a `PaymentService`":

1. First the **ambiguity** error is observed.
2. Then it is resolved **separately** with `@Primary`.
3. Then with `@Qualifier`.

Every successful payment is saved to MySQL, so the table shows which implementation Spring injected.

## Tech

Java 17 · Maven · Spring Framework 6.1.14 · MySQL 8 (`mysql-connector-j`)

**Database:** shared Day 07 database `Day_007_Assigments`. Table **`a05_payments`** uses the `a05_` prefix, so it is uniquely identifiable as Assignment 5.

## Structure

```text
src/main/java/com/company/spring
├── payment/
│   ├── PaymentService            interface
│   ├── CardPaymentService        implementation 1 (CARD)
│   ├── UpiPaymentService         implementation 2 (UPI)
│   ├── WalletPaymentService      implementation 3 (WALLET) - optional extension
│   └── PaymentReceipt
├── repository/PaymentRepository  saves/reads a05_payments
├── checkout/
│   ├── basic/CheckoutService              asks for PaymentService - no qualifier
│   ├── qualified/QualifiedCheckoutService @Qualifier("upiPayment") on the constructor parameter
│   └── multi/PaymentRouter                Map<String, PaymentService> - all implementations
├── config/
│   ├── CommonConfig               DataSource + PaymentRepository (imported by all)
│   ├── AmbiguousConfig            2 beans, no @Primary              -> STEP 1 (error)
│   ├── PrimaryConfig              2 beans, @Primary on cardPayment   -> SOLUTION 1
│   ├── QualifierConfig            2 beans, no @Primary               -> SOLUTION 2 (with QualifiedCheckoutService)
│   └── ThreeImplementationsConfig 3 beans, @Primary + @Qualifier + router -> EXTENSION
└── app/   AmbiguityDemo, PrimaryDemo, QualifierDemo, ThirdImplementationDemo (+ PaymentPrinter)
```

**Why the implementations have no `@Service`.** They are registered by `@Bean` methods, with the bean name taken from the method name (`cardPayment`, `upiPayment`, `walletPayment`). Each scenario configuration can then decide independently whether one is `@Primary`, so the three cases run **separately** from the same code with no editing in between. With component scanning you would write `@Service @Primary` on the class itself; the effect is identical.

## How to run

1. Run `src/main/resources/schema.sql` in MySQL. It creates the database if needed and drops/recreates only `a05_payments`.
2. In Eclipse: **File → Import → Maven → Existing Maven Projects**, select `05-primary-qualifier`, and set the JRE to **JavaSE-17**.
3. Run, in order: `AmbiguityDemo` → `PrimaryDemo` → `QualifierDemo` → `ThirdImplementationDemo`.

## Step 1: the ambiguity (`AmbiguityDemo`)

```text
STEP 1 - two PaymentService beans (cardPayment, upiPayment), no @Primary, no @Qualifier

STARTUP FAILED - ambiguous bean:
   org.springframework.beans.factory.UnsatisfiedDependencyException
   Error creating bean with name 'checkoutService' ...: Unsatisfied dependency expressed through
   constructor parameter 0: No qualifying bean of type 'com.company.spring.payment.PaymentService'
   available: expected single matching bean but found 2: cardPayment,upiPayment

   Root cause: org.springframework.beans.factory.NoUniqueBeanDefinitionException
   No qualifying bean of type 'com.company.spring.payment.PaymentService' available:
   expected single matching bean but found 2: cardPayment,upiPayment
```

Spring finds **two** beans of the requested type and has no rule for choosing, so it refuses to start. The message lists the candidates (`cardPayment,upiPayment`). Compare Assignment 2: there it was *zero* matching beans (`expected at least 1`); here it is *too many* (`expected single matching bean but found 2`).

## Solution 1: `@Primary` (`PrimaryDemo`)

```text
SOLUTION 1 - @Primary on cardPayment (CheckoutService unchanged)

   [CheckoutService] injected PaymentService = CardPaymentService
Container started.

   Checkout ORD-5001 for Rs. 2499.00 using CARD
   Card payment: charging the saved Visa card ending 4242
   Saved to a05_payments: 1     ORD-5001   Rs. 2499.00  CARD    CARD-B12636CE

   Is cardPayment primary? true
   Is upiPayment primary?  false
```

Only change: `@Primary` on the `cardPayment` bean. `CheckoutService` was **not modified**, and it now receives the card implementation.

## Solution 2: `@Qualifier` (`QualifierDemo`)

```text
SOLUTION 2 - no @Primary; @Qualifier("upiPayment") at the injection point

   [QualifiedCheckoutService] injected PaymentService = UpiPaymentService  (@Qualifier("upiPayment"))
Container started.

   Checkout ORD-5002 for Rs. 899.50 using UPI
   UPI payment: collect request sent to ravi@okbank
   Saved to a05_payments: 2     ORD-5002    Rs. 899.50  UPI     UPI-B0120FCB
```

The two beans are the same as in Step 1, with **no `@Primary`**. The consumer resolves the ambiguity itself:

```java
public QualifiedCheckoutService(@Qualifier("upiPayment") PaymentService paymentService)
```

## Optional extension: third implementation (`ThirdImplementationDemo`)

```text
EXTENSION - 3 implementations; cardPayment is @Primary

   [CheckoutService] injected PaymentService = CardPaymentService
   [QualifiedCheckoutService] injected PaymentService = UpiPaymentService  (@Qualifier("upiPayment"))
   [PaymentRouter] injected ALL PaymentService beans: [cardPayment, upiPayment, walletPayment]
Container started.

1. CheckoutService (no qualifier) -> gets the @Primary bean:
   Saved to a05_payments: 3     ORD-5003   Rs. 1200.00  CARD    CARD-5A76E591

2. QualifiedCheckoutService -> @Qualifier wins over @Primary:
   Saved to a05_payments: 4     ORD-5004    Rs. 350.00  UPI     UPI-D7E89E07

3. PaymentRouter -> every implementation, chosen at run time:
   Router: ORD-5005 -> WALLET
   Saved to a05_payments: 5     ORD-5005    Rs. 150.00  WALLET  WALLET-BF6FF373
   Router: ORD-5006 -> UPI
   Saved to a05_payments: 6     ORD-5006    Rs. 640.00  UPI     UPI-ED2B2EC2
```

- **Adding `WalletPaymentService` needed no change to `CheckoutService`.** It keeps getting the `@Primary` bean.
- **With both present, `@Qualifier` overrides `@Primary`.** The more specific instruction wins.
- **Injecting `Map<String, PaymentService>` (or `List<PaymentService>`) avoids the choice entirely:** Spring injects *every* implementation, keyed by bean name. This is the right design when the user chooses the payment method at run time.

**Database evidence:**

```sql
SELECT payment_id, order_id, amount, payment_method FROM Day_007_Assigments.a05_payments;
```

You'll see CARD, UPI, CARD, UPI, WALLET, UPI, matching the injected beans. Re-running `schema.sql` empties the table.

## Explanation: when to use `@Primary` vs `@Qualifier`

| | `@Primary` | `@Qualifier("name")` |
|---|---|---|
| Where it goes | On the **bean** (implementation class or `@Bean` method) | At the **injection point** (constructor/setter parameter or field) |
| Who decides | The provider: "I am the default" | The consumer: "I need exactly this one" |
| Effect | Used whenever several candidates match and nobody asked for a specific one | Selects one bean by name; **overrides** `@Primary` |
| Limit | Only one bean of a type should be primary (two primaries = ambiguity again) | Bean names are strings, so a typo is only caught at startup |

**Use `@Primary`** when one implementation is the normal choice for almost everyone, e.g. the standard payment gateway. The few classes that need another one can override it with `@Qualifier`.

**Use `@Qualifier`** when different consumers genuinely need different implementations (a refund flow must use UPI, the main checkout uses card), or when there is no sensible default. Keep the names meaningful, and consider custom qualifier annotations in large projects.

**Inject a `List`/`Map` of all implementations** when the choice is made at run time, as in `PaymentRouter`.
