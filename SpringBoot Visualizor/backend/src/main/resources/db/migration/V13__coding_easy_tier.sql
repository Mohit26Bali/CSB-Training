-- Coding practice (Phase F6): an EASY tier of everyday exercises in the Java Core and Spring Boot Basics topics.
--
-- Schema: nothing new. The one change is the CHECK on coding_problems.harness_kind, which now also names the ten new harness kinds. A kind selects
-- a fixed, trusted contract in the judge worker (a class name and its public methods); it is chosen from this column by the backend and can never
-- come from a request.
--
-- A test here is a SCRIPT ("new ..." builds the learner's object, "call method args" calls a public method) in input_spec and the EXPECTED
-- observation of every operation in expected_spec. The expected traces were computed by independent Python models, and the Java reference
-- solutions run in the real sandbox must reproduce them.
--
-- Nothing that exists is changed: the pilot, the five microservices problems and every learner's history are untouched. Each problem is version 1.

ALTER TABLE coding_problems DROP CHECK ck_coding_problems_harness;
ALTER TABLE coding_problems ADD CONSTRAINT ck_coding_problems_harness CHECK (harness_kind IN ('RETRY_EXECUTOR', 'CIRCUIT_BREAKER', 'BULKHEAD', 'IDEMPOTENCY', 'SAGA', 'OUTBOX', 'PASSWORD_VALIDATOR', 'LOAN_ELIGIBILITY', 'SALARY_CALCULATOR', 'BANK_ACCOUNT', 'TRAINING_REGISTRY', 'GRADE_CALCULATOR', 'REQUEST_VALIDATOR', 'PRODUCT_SERVICE', 'ERROR_MAPPER', 'BEAN_REGISTRY'));

-- ---------------------------------------------------------------------------------------------------------------------------------
-- Password validator
INSERT INTO coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at)
SELECT id, 'password-validator', 'Password validator', 'EASY', 'java17', 'PASSWORD_VALIDATOR', 1, TRUE, NOW(6), NOW(6) FROM topics WHERE slug = 'java-core';
SET @problem = LAST_INSERT_ID();

INSERT INTO coding_problem_versions (problem_id, version, summary, statement_md, objectives_json, constraints_md, examples_json, starter_code, created_at)
VALUES (@problem, 1, 'Check a password against a list of rules, report the first rule it breaks, and throw a custom checked exception.',
 '## Password validator

A sign-up form must refuse weak passwords and tell the user the **first** rule that is broken. You write the validator.

### Your task

Write `public class PasswordValidator` with these public methods:

```java
public class PasswordValidator {
    public String firstError(String password)
    public void validate(String password) throws PasswordValidationException
}
```

`PasswordValidationException` is provided (a checked exception with a `String` message constructor); you do not declare it.

### Rules (checked in this order)

1. `null` or an empty string: the error is `empty`.
2. Fewer than 8 characters: `too short`.
3. No uppercase letter (A to Z): `no uppercase`.
4. No lowercase letter (a to z): `no lowercase`.
5. No digit: `no digit`.
6. No special character, meaning every character is a letter or a digit: `no special character`. Any other character, a space included, counts as special.

`firstError` returns the error text of the first rule that fails, or `OK` when none fails.
`validate` does nothing for a good password and throws `PasswordValidationException` (with the error text as its message) otherwise.

### In this exercise

This is plain Java with no framework, no database and no network. The tests build your object with `new` and call its public methods, so the behaviour is exactly what the rules below say.',
 '["Apply several rules in a fixed order and stop at the first one that fails.", "Handle null and empty input before anything else.", "Throw and declare a checked exception.", "Reuse one method inside another instead of copying the rules."]',
 '* A password can be `null`. In the tests the word `null` means a null string, `empty` means `""`, and `_` stands for a space.
* Only ASCII letters and digits appear in the tests.
* Do not print anything.',
 '[{"title": "A good password", "input": "firstError(\\"Abcdef1@\\")", "output": "OK", "explanation": "It has 8 characters, an uppercase letter, a lowercase letter, a digit and a special character."}, {"title": "The first broken rule wins", "input": "firstError(\\"abcdefgh\\")", "output": "no uppercase", "explanation": "Long enough, but there is no uppercase letter. Lowercase, digit and special are also missing, yet only the first failure is reported."}]',
 'public class PasswordValidator {

    public String firstError(String password) {
        // TODO: return "empty", "too short", "no uppercase", "no lowercase", "no digit", "no special character" or "OK"
        return null;
    }

    public void validate(String password) throws PasswordValidationException {
        // TODO: throw PasswordValidationException when firstError is not "OK"
    }
}
', NOW(6));
SET @version = LAST_INSERT_ID();
INSERT INTO coding_test_cases (problem_version_id, sort_order, name, concept, visible, input_spec, expected_spec) VALUES
 (@version, 0, 'A strong password is accepted', 'happy path', TRUE, '{"script": ["new", "call firstError Abcdef1@", "call validate Abcdef1@"]}', '{"trace": ["-", "OK", "-"]}'),
 (@version, 1, 'Too short is reported', 'length', TRUE, '{"script": ["new", "call firstError Ab1@", "call firstError empty"]}', '{"trace": ["-", "too short", "empty"]}'),
 (@version, 2, 'validate throws for a weak password', 'exception', TRUE, '{"script": ["new", "call validate abcdefgh", "call validate Abcdef1@"]}', '{"trace": ["-", "EX:PasswordValidationException", "-"]}'),
 (@version, 3, 'Eight characters are enough, seven are not', 'boundary', FALSE, '{"script": ["new", "call firstError Abcdef1@", "call firstError Abcde1@"]}', '{"trace": ["-", "OK", "too short"]}'),
 (@version, 4, 'The first broken rule is the one reported', 'order', FALSE, '{"script": ["new", "call firstError abcdefg", "call firstError abcdefgh", "call firstError ABCDEFGH", "call firstError Abcdefgh", "call firstError Abcdefg1", "call firstError Abcdefg1@"]}', '{"trace": ["-", "too short", "no uppercase", "no lowercase", "no digit", "no special character", "OK"]}'),
 (@version, 5, 'Null and empty are the same error', 'null handling', FALSE, '{"script": ["new", "call firstError null", "call firstError empty", "call validate null", "call validate empty"]}', '{"trace": ["-", "empty", "empty", "EX:PasswordValidationException", "EX:PasswordValidationException"]}'),
 (@version, 6, 'A space is a special character', 'special', FALSE, '{"script": ["new", "call firstError Abcdefg1_", "call validate Abcdefg1_"]}', '{"trace": ["-", "OK", "-"]}'),
 (@version, 7, 'validate throws for each missing rule', 'exception', FALSE, '{"script": ["new", "call validate Abcdefg1", "call validate abcdef1@", "call validate ABCDEF1@", "call validate Abcdefg@", "call validate Abcdef1-"]}', '{"trace": ["-", "EX:PasswordValidationException", "EX:PasswordValidationException", "EX:PasswordValidationException", "EX:PasswordValidationException", "-"]}');

-- ---------------------------------------------------------------------------------------------------------------------------------
-- Loan eligibility
INSERT INTO coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at)
SELECT id, 'loan-eligibility', 'Loan eligibility', 'EASY', 'java17', 'LOAN_ELIGIBILITY', 1, TRUE, NOW(6), NOW(6) FROM topics WHERE slug = 'java-core';
SET @problem = LAST_INSERT_ID();

INSERT INTO coding_problem_versions (problem_id, version, summary, statement_md, objectives_json, constraints_md, examples_json, starter_code, created_at)
VALUES (@problem, 1, 'Decide a loan application from age, income, credit score and amount, applying the rules in order and getting every boundary right.',
 '## Loan eligibility

A bank decides loan applications with a short written policy. You turn the policy into code.

### Your task

Write `public class LoanEligibility` with these public methods:

```java
public class LoanEligibility {
    public double maxLoan(double monthlyIncome)
    public String decide(int age, double monthlyIncome, int creditScore, double requestedAmount)
}
```

### Rules

`maxLoan` is 20 times the monthly income, but never more than 2000000.

`decide` checks the rules **in this order** and returns the text of the first that applies:

1. Age below 21 or above 60: `REJECTED:AGE` (21 and 60 are allowed).
2. Credit score below 600: `REJECTED:CREDIT` (600 is allowed).
3. Monthly income below 25000: `REJECTED:INCOME` (25000 is allowed).
4. Requested amount above `maxLoan(monthlyIncome)`: `REJECTED:AMOUNT` (exactly the maximum is allowed).
5. Credit score below 700: `APPROVED:MANUAL_REVIEW`.
6. Otherwise: `APPROVED`.

### In this exercise

This is plain Java with no framework, no database and no network. The tests build your object with `new` and call its public methods, so the behaviour is exactly what the rules below say.',
 '["Turn a written policy into if/else rules, in the right order.", "Get inclusive and exclusive boundaries exactly right.", "Use one method (maxLoan) from another (decide).", "Return a status text instead of printing."]',
 '* Amounts are plain numbers such as `50000` or `250000.5`. A `double` result is shown with two decimals in the tests, for example `1000000.00`.
* The order of the rules matters when an application breaks more than one.
* Do not print anything.',
 '[{"title": "A normal approval", "input": "decide(30, 50000, 750, 500000)", "output": "APPROVED", "explanation": "Age, credit and income are fine; 500000 is below the maximum of 1000000 and the score is at least 700."}, {"title": "Two rules fail, the first one is reported", "input": "decide(19, 10000, 500, 100000)", "output": "REJECTED:AGE", "explanation": "The age rule comes first, so it wins over the credit and income problems."}]',
 'public class LoanEligibility {

    public double maxLoan(double monthlyIncome) {
        // TODO: 20 times the income, at most 2000000
        return 0;
    }

    public String decide(int age, double monthlyIncome, int creditScore, double requestedAmount) {
        // TODO: apply the rules in order
        return null;
    }
}
', NOW(6));
SET @version = LAST_INSERT_ID();
INSERT INTO coding_test_cases (problem_version_id, sort_order, name, concept, visible, input_spec, expected_spec) VALUES
 (@version, 0, 'An applicant who meets every rule is approved', 'happy path', TRUE, '{"script": ["new", "call decide 30 50000 750 500000"]}', '{"trace": ["-", "APPROVED"]}'),
 (@version, 1, 'A low credit score is rejected', 'rule', TRUE, '{"script": ["new", "call decide 30 50000 550 100000"]}', '{"trace": ["-", "REJECTED:CREDIT"]}'),
 (@version, 2, 'An applicant who is too young is rejected', 'rule', TRUE, '{"script": ["new", "call decide 19 50000 750 100000"]}', '{"trace": ["-", "REJECTED:AGE"]}'),
 (@version, 3, 'Age 21 and 60 are allowed, 20 and 61 are not', 'boundary', FALSE, '{"script": ["new", "call decide 21 50000 750 100000", "call decide 60 50000 750 100000", "call decide 20 50000 750 100000", "call decide 61 50000 750 100000"]}', '{"trace": ["-", "APPROVED", "APPROVED", "REJECTED:AGE", "REJECTED:AGE"]}'),
 (@version, 4, 'Credit scores around 600 and 700', 'boundary', FALSE, '{"script": ["new", "call decide 30 50000 599 100000", "call decide 30 50000 600 100000", "call decide 30 50000 699 100000", "call decide 30 50000 700 100000"]}', '{"trace": ["-", "REJECTED:CREDIT", "APPROVED:MANUAL_REVIEW", "APPROVED:MANUAL_REVIEW", "APPROVED"]}'),
 (@version, 5, 'Income just below and at the minimum', 'boundary', FALSE, '{"script": ["new", "call decide 30 24999 750 100000", "call decide 30 25000 750 100000"]}', '{"trace": ["-", "REJECTED:INCOME", "APPROVED"]}'),
 (@version, 6, 'The requested amount may equal the maximum but not exceed it', 'boundary', FALSE, '{"script": ["new", "call decide 30 50000 750 1000000", "call decide 30 50000 750 1000001"]}', '{"trace": ["-", "APPROVED", "REJECTED:AMOUNT"]}'),
 (@version, 7, 'The maximum loan is capped', 'cap', FALSE, '{"script": ["new", "call maxLoan 50000", "call maxLoan 100000", "call maxLoan 200000", "call maxLoan 25000"]}', '{"trace": ["-", "1000000.00", "2000000.00", "2000000.00", "500000.00"]}'),
 (@version, 8, 'The rules are applied in order', 'order', FALSE, '{"script": ["new", "call decide 19 10000 500 9999999", "call decide 30 10000 500 9999999", "call decide 30 10000 750 9999999", "call decide 30 30000 750 9999999"]}', '{"trace": ["-", "REJECTED:AGE", "REJECTED:CREDIT", "REJECTED:INCOME", "REJECTED:AMOUNT"]}');

-- ---------------------------------------------------------------------------------------------------------------------------------
-- Salary calculator
INSERT INTO coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at)
SELECT id, 'salary-calculator', 'Salary calculator', 'EASY', 'java17', 'SALARY_CALCULATOR', 1, TRUE, NOW(6), NOW(6) FROM topics WHERE slug = 'java-core';
SET @problem = LAST_INSERT_ID();

INSERT INTO coding_problem_versions (problem_id, version, summary, statement_md, objectives_json, constraints_md, examples_json, starter_code, created_at)
VALUES (@problem, 1, 'Calculate the net monthly pay of permanent, contract and intern employees, each with its own rule, and reject invalid input.',
 '## Salary calculator

The payroll team pays three kinds of employees, each in a different way. You write the calculator.

### Your task

```java
public class SalaryCalculator {
    public double netSalary(String type, double baseSalary, int daysWorked)
}
```

### Rules

* **PERMANENT:** gross pay is the base salary plus 20% (that is, `base * 1.2`). Tax depends on the **base salary**: above 80000 the tax is 20% of the gross, above 40000 it is 10% of the gross, otherwise 0. The net pay is gross minus tax. Days worked are not used.
* **CONTRACT:** paid per day. The daily rate is `base / 30`, the gross is the daily rate times the days worked, and 5% platform fee is taken: `net = base / 30 * daysWorked * 0.95`. No tax.
* **INTERN:** a fixed stipend: the base salary, but never more than 15000. Days worked are not used.
* Throw `IllegalArgumentException` when the type is not one of the three words above (they are case-sensitive), when the base salary is negative, or when the days worked are below 0 or above 30. These checks apply to every type.

### In this exercise

This is plain Java with no framework, no database and no network. The tests build your object with `new` and call its public methods, so the behaviour is exactly what the rules below say.',
 '["Choose a calculation by employee type.", "Apply a tax band that depends on the base salary.", "Validate inputs and throw IllegalArgumentException.", "Work with double values and percentages."]',
 '* The tests show a `double` result with two decimals, for example `36000.00`. Every value in the tests is exact to two decimals.
* "Above 40000" means strictly greater: a base salary of exactly 40000 is not taxed.
* Do not print anything.',
 '[{"title": "A permanent employee below the tax bands", "input": "netSalary(\\"PERMANENT\\", 30000, 0)", "output": "36000.00", "explanation": "Gross is 30000 * 1.2 = 36000. The base salary is not above 40000, so there is no tax."}, {"title": "A contract worker", "input": "netSalary(\\"CONTRACT\\", 30000, 15)", "output": "14250.00", "explanation": "30000 / 30 * 15 = 15000, minus the 5% fee: 14250."}]',
 'public class SalaryCalculator {

    public double netSalary(String type, double baseSalary, int daysWorked) {
        // TODO: validate the input, then calculate by type
        return 0;
    }
}
', NOW(6));
SET @version = LAST_INSERT_ID();
INSERT INTO coding_test_cases (problem_version_id, sort_order, name, concept, visible, input_spec, expected_spec) VALUES
 (@version, 0, 'A permanent employee with no tax', 'permanent', TRUE, '{"script": ["new", "call netSalary PERMANENT 30000 0"]}', '{"trace": ["-", "36000.00"]}'),
 (@version, 1, 'A contract worker is paid by the day', 'contract', TRUE, '{"script": ["new", "call netSalary CONTRACT 30000 15"]}', '{"trace": ["-", "14250.00"]}'),
 (@version, 2, 'An intern gets the stipend', 'intern', TRUE, '{"script": ["new", "call netSalary INTERN 12000 10"]}', '{"trace": ["-", "12000.00"]}'),
 (@version, 3, 'The tax bands start above 40000 and above 80000', 'boundary', FALSE, '{"script": ["new", "call netSalary PERMANENT 40000 0", "call netSalary PERMANENT 40001 0", "call netSalary PERMANENT 80000 0", "call netSalary PERMANENT 80001 0"]}', '{"trace": ["-", "48000.00", "43201.08", "86400.00", "76800.96"]}'),
 (@version, 4, 'Tax bands for typical salaries', 'bands', FALSE, '{"script": ["new", "call netSalary PERMANENT 60000 0", "call netSalary PERMANENT 100000 0"]}', '{"trace": ["-", "64800.00", "96000.00"]}'),
 (@version, 5, 'A contract worker for a full month and for no days', 'contract', FALSE, '{"script": ["new", "call netSalary CONTRACT 30000 30", "call netSalary CONTRACT 30000 0"]}', '{"trace": ["-", "28500.00", "0.00"]}'),
 (@version, 6, 'The intern stipend is capped', 'cap', FALSE, '{"script": ["new", "call netSalary INTERN 15000 30", "call netSalary INTERN 20000 30", "call netSalary INTERN 9000 1"]}', '{"trace": ["-", "15000.00", "15000.00", "9000.00"]}'),
 (@version, 7, 'Unknown types are rejected', 'validation', FALSE, '{"script": ["new", "call netSalary BOSS 30000 10", "call netSalary permanent 30000 10", "call netSalary empty 30000 10"]}', '{"trace": ["-", "EX:IllegalArgumentException", "EX:IllegalArgumentException", "EX:IllegalArgumentException"]}'),
 (@version, 8, 'Negative money and impossible days are rejected', 'validation', FALSE, '{"script": ["new", "call netSalary PERMANENT -1 10", "call netSalary CONTRACT 30000 31", "call netSalary INTERN 12000 -1", "call netSalary INTERN 12000 30"]}', '{"trace": ["-", "EX:IllegalArgumentException", "EX:IllegalArgumentException", "EX:IllegalArgumentException", "12000.00"]}');

-- ---------------------------------------------------------------------------------------------------------------------------------
-- Bank account
INSERT INTO coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at)
SELECT id, 'bank-account', 'Bank account', 'EASY', 'java17', 'BANK_ACCOUNT', 1, TRUE, NOW(6), NOW(6) FROM topics WHERE slug = 'java-core';
SET @problem = LAST_INSERT_ID();

INSERT INTO coding_problem_versions (problem_id, version, summary, statement_md, objectives_json, constraints_md, examples_json, starter_code, created_at)
VALUES (@problem, 1, 'Model a bank account with deposits, withdrawals, a balance that never goes wrong, and a custom checked exception for insufficient funds.',
 '## Bank account

An account protects its own balance: callers can only deposit and withdraw, and a bad request must leave the account exactly as it was.

### Your task

```java
public class BankAccount {
    public BankAccount(String owner, double openingBalance)
    public void deposit(double amount)
    public void withdraw(double amount) throws InsufficientBalanceException
    public double getBalance()
    public String getOwner()
    public int transactionCount()
}
```

`InsufficientBalanceException` is provided (a checked exception with a `String` message constructor).

### Rules

* The constructor throws `IllegalArgumentException` when the owner is `null` or blank, or when the opening balance is negative.
* `deposit` adds the amount. An amount of zero or less throws `IllegalArgumentException`.
* `withdraw` subtracts the amount. An amount of zero or less throws `IllegalArgumentException`. An amount **greater than the balance** throws `InsufficientBalanceException` (withdrawing exactly the whole balance is allowed).
* `transactionCount` counts the deposits and withdrawals that **succeeded**. Rejected ones are not counted and change nothing.

### In this exercise

This is plain Java with no framework, no database and no network. The tests build your object with `new` and call its public methods, so the behaviour is exactly what the rules below say.',
 '["Keep an object consistent: a failed operation must change nothing.", "Validate constructor and method arguments.", "Throw and declare a custom checked exception.", "Count only the operations that succeeded."]',
 '* Money is a `double`. The tests show balances with two decimals, for example `1300.00`.
* The tests build the account with `new Owner 1000`: the first word is the owner, the second the opening balance.
* Do not print anything.',
 '[{"title": "Deposit, then withdraw", "input": "new BankAccount(\\"Mohit\\", 1000); deposit(500); withdraw(200); getBalance()", "output": "1300.00", "explanation": "1000 + 500 - 200."}, {"title": "Too much is refused", "input": "withdraw(5000) on a balance of 1000", "output": "InsufficientBalanceException, balance still 1000.00", "explanation": "The failed withdrawal changes nothing."}]',
 'public class BankAccount {

    public BankAccount(String owner, double openingBalance) {
        // TODO: validate and store
    }

    public void deposit(double amount) {
        // TODO
    }

    public void withdraw(double amount) throws InsufficientBalanceException {
        // TODO
    }

    public double getBalance() {
        return 0;
    }

    public String getOwner() {
        return null;
    }

    public int transactionCount() {
        return 0;
    }
}
', NOW(6));
SET @version = LAST_INSERT_ID();
INSERT INTO coding_test_cases (problem_version_id, sort_order, name, concept, visible, input_spec, expected_spec) VALUES
 (@version, 0, 'A deposit and a withdrawal change the balance', 'happy path', TRUE, '{"script": ["new Mohit 1000", "call deposit 500", "call withdraw 200", "call getBalance"]}', '{"trace": ["-", "-", "-", "1300.00"]}'),
 (@version, 1, 'Too much cannot be withdrawn', 'exception', TRUE, '{"script": ["new Mohit 1000", "call withdraw 5000", "call getBalance"]}', '{"trace": ["-", "EX:InsufficientBalanceException", "1000.00"]}'),
 (@version, 2, 'A new account reports its owner and balance', 'constructor', TRUE, '{"script": ["new Asha 250", "call getOwner", "call getBalance", "call transactionCount"]}', '{"trace": ["-", "Asha", "250.00", "0"]}'),
 (@version, 3, 'The whole balance can be withdrawn', 'boundary', FALSE, '{"script": ["new Mohit 1000", "call withdraw 1000", "call getBalance", "call withdraw 0.5"]}', '{"trace": ["-", "-", "0.00", "EX:InsufficientBalanceException"]}'),
 (@version, 4, 'Zero and negative amounts are rejected', 'validation', FALSE, '{"script": ["new Mohit 1000", "call deposit 0", "call deposit -5", "call withdraw 0", "call withdraw -5", "call getBalance"]}', '{"trace": ["-", "EX:IllegalArgumentException", "EX:IllegalArgumentException", "EX:IllegalArgumentException", "EX:IllegalArgumentException", "1000.00"]}'),
 (@version, 5, 'Only successful operations are counted', 'counting', FALSE, '{"script": ["new Mohit 1000", "call deposit 100", "call withdraw 5000", "call deposit -1", "call withdraw 100", "call transactionCount"]}', '{"trace": ["-", "-", "EX:InsufficientBalanceException", "EX:IllegalArgumentException", "-", "2"]}'),
 (@version, 6, 'A bad constructor argument is rejected', 'constructor', FALSE, '{"script": ["new empty 100", "call getBalance", "new Mohit -1", "new null 100", "new _ 100"]}', '{"trace": ["EX:IllegalArgumentException", "EX:IllegalStateException", "EX:IllegalArgumentException", "EX:IllegalArgumentException", "EX:IllegalArgumentException"]}'),
 (@version, 7, 'A failed withdrawal leaves everything unchanged', 'consistency', FALSE, '{"script": ["new Mohit 100", "call withdraw 100.5", "call getBalance", "call transactionCount"]}', '{"trace": ["-", "EX:InsufficientBalanceException", "100.00", "0"]}');

-- ---------------------------------------------------------------------------------------------------------------------------------
-- Training registration
INSERT INTO coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at)
SELECT id, 'training-registry', 'Training registration', 'EASY', 'java17', 'TRAINING_REGISTRY', 1, TRUE, NOW(6), NOW(6) FROM topics WHERE slug = 'java-core';
SET @problem = LAST_INSERT_ID();

INSERT INTO coding_problem_versions (problem_id, version, summary, statement_md, objectives_json, constraints_md, examples_json, starter_code, created_at)
VALUES (@problem, 1, 'Register participants for technologies with collections: refuse duplicates, enforce a capacity, and answer sorted and ordered queries.',
 '## Training registration

A training centre registers participants for technologies. You keep the registrations in memory.

### Your task

```java
public class TrainingRegistry {
    public boolean register(String participant, String technology)
    public int countFor(String technology)
    public List<String> technologiesOf(String participant)
    public List<String> participantsIn(String technology)
}
```

### Rules

* `register` adds the participant to the technology and returns `true`. If that participant is **already** registered for that technology it changes nothing and returns `false`.
* A technology holds **at most 3** participants. Registering a fourth different participant throws `IllegalStateException`. (A duplicate of an existing participant still returns `false`, even when the class is full.)
* A `null` or blank participant or technology throws `IllegalArgumentException`.
* `countFor` is the number of participants in a technology (0 for an unknown one).
* `technologiesOf` lists the technologies a participant is in, **sorted alphabetically**. Nothing found gives an empty list.
* `participantsIn` lists the participants of a technology in **the order they registered**. Nothing found gives an empty list.

### In this exercise

This is plain Java with no framework, no database and no network. The tests build your object with `new` and call its public methods, so the behaviour is exactly what the rules below say.',
 '["Choose collections for the data: a list per technology, ordered by registration.", "Detect duplicates before adding.", "Return sorted results and keep insertion order where asked.", "Reject invalid input and a full class with the right exceptions."]',
 '* In the tests a list is shown as its items joined by commas, and an empty list as `-`.
* Names are case-sensitive: `java` and `Java` are different technologies.
* Do not print anything.',
 '[{"title": "A duplicate is refused", "input": "register(\\"Asha\\", \\"Java\\"); register(\\"Asha\\", \\"Java\\")", "output": "true, then false", "explanation": "The second call finds the pair and changes nothing."}, {"title": "Sorted technologies", "input": "Asha registers for Spring, then Java; technologiesOf(\\"Asha\\")", "output": "Java,Spring", "explanation": "The result is sorted, not in registration order."}]',
 'import java.util.ArrayList;
import java.util.List;

public class TrainingRegistry {

    public boolean register(String participant, String technology) {
        // TODO
        return false;
    }

    public int countFor(String technology) {
        // TODO
        return 0;
    }

    public List<String> technologiesOf(String participant) {
        // TODO: sorted alphabetically
        return new ArrayList<>();
    }

    public List<String> participantsIn(String technology) {
        // TODO: in registration order
        return new ArrayList<>();
    }
}
', NOW(6));
SET @version = LAST_INSERT_ID();
INSERT INTO coding_test_cases (problem_version_id, sort_order, name, concept, visible, input_spec, expected_spec) VALUES
 (@version, 0, 'Registering and counting', 'happy path', TRUE, '{"script": ["new", "call register Asha Java", "call register Ravi Java", "call countFor Java"]}', '{"trace": ["-", "true", "true", "2"]}'),
 (@version, 1, 'A duplicate registration is refused', 'duplicate', TRUE, '{"script": ["new", "call register Asha Java", "call register Asha Java", "call countFor Java"]}', '{"trace": ["-", "true", "false", "1"]}'),
 (@version, 2, 'Technologies of a participant are sorted', 'sorting', TRUE, '{"script": ["new", "call register Asha Spring", "call register Asha Java", "call register Asha Docker", "call technologiesOf Asha"]}', '{"trace": ["-", "true", "true", "true", "Docker,Java,Spring"]}'),
 (@version, 3, 'Participants keep their registration order', 'ordering', FALSE, '{"script": ["new", "call register Zoya Java", "call register Asha Java", "call register Ravi Java", "call participantsIn Java"]}', '{"trace": ["-", "true", "true", "true", "Zoya,Asha,Ravi"]}'),
 (@version, 4, 'A class holds three participants', 'capacity', FALSE, '{"script": ["new", "call register A Java", "call register B Java", "call register C Java", "call register D Java", "call countFor Java", "call participantsIn Java"]}', '{"trace": ["-", "true", "true", "true", "EX:IllegalStateException", "3", "A,B,C"]}'),
 (@version, 5, 'A duplicate in a full class is still false', 'capacity', FALSE, '{"script": ["new", "call register A Java", "call register B Java", "call register C Java", "call register B Java"]}', '{"trace": ["-", "true", "true", "true", "false"]}'),
 (@version, 6, 'Other technologies have their own capacity', 'independence', FALSE, '{"script": ["new", "call register A Java", "call register B Java", "call register C Java", "call register D Spring", "call countFor Spring", "call countFor Java"]}', '{"trace": ["-", "true", "true", "true", "true", "1", "3"]}'),
 (@version, 7, 'Unknown participants and technologies give empty results', 'empty', FALSE, '{"script": ["new", "call countFor Rust", "call technologiesOf Nobody", "call participantsIn Rust"]}', '{"trace": ["-", "0", "-", "-"]}'),
 (@version, 8, 'Blank names are rejected', 'validation', FALSE, '{"script": ["new", "call register empty Java", "call register Asha empty", "call register null Java", "call register _ Java", "call countFor Java"]}', '{"trace": ["-", "EX:IllegalArgumentException", "EX:IllegalArgumentException", "EX:IllegalArgumentException", "EX:IllegalArgumentException", "0"]}'),
 (@version, 9, 'Java and java are different technologies', 'case', FALSE, '{"script": ["new", "call register Asha Java", "call register asha Java", "call register Asha java", "call countFor Java", "call countFor java"]}', '{"trace": ["-", "true", "true", "true", "2", "1"]}');

-- ---------------------------------------------------------------------------------------------------------------------------------
-- Fix the grade calculator
INSERT INTO coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at)
SELECT id, 'grade-calculator-fix', 'Fix the grade calculator', 'EASY', 'java17', 'GRADE_CALCULATOR', 1, TRUE, NOW(6), NOW(6) FROM topics WHERE slug = 'java-core';
SET @problem = LAST_INSERT_ID();

INSERT INTO coding_problem_versions (problem_id, version, summary, statement_md, objectives_json, constraints_md, examples_json, starter_code, created_at)
VALUES (@problem, 1, 'The grade calculator on screen has three bugs. Read it, find them, and fix it so it follows the rules.',
 '## Fix the grade calculator

The starter code already compiles and runs, but it gives wrong answers. **Find the bugs and fix them.** You may rewrite any method; the public methods must keep their signatures.

### What the code must do

```java
public class GradeCalculator {
    public double average(int a, int b, int c)
    public String grade(double average)
    public boolean isPassing(int a, int b, int c)
}
```

* `average` is the exact mean of the three marks (for marks 80, 85 and 90 it is 85.00; for 70, 70 and 71 it is 70.33).
* `grade`: 90 or more is `A`, 75 or more is `B`, 60 or more is `C`, anything lower is `F`.
* `isPassing` is true only when the average is at least 60 **and** no single mark is below 35.

### Hints

Three different kinds of mistake are hidden in the starter code: one about numbers, one about a boundary and one about comparing text. Run the sample tests to see which answers are wrong.

### In this exercise

This is plain Java with no framework, no database and no network. The tests build your object with `new` and call its public methods, so the behaviour is exactly what the rules below say.',
 '["Read existing code and find defects instead of writing from scratch.", "Recognise integer division.", "Fix a boundary that is off by one.", "Understand why strings are compared with equals, not with !=."]',
 '* Marks are whole numbers from 0 to 100.
* A `double` result is shown with two decimals, for example `85.00`.
* Do not print anything.',
 '[{"title": "The average must not lose its fraction", "input": "average(70, 70, 71)", "output": "70.33", "explanation": "Dividing integers would give 70."}, {"title": "A boundary mark", "input": "grade(90.0)", "output": "A", "explanation": "Exactly 90 is an A."}]',
 'public class GradeCalculator {

    public double average(int a, int b, int c) {
        return (a + b + c) / 3;
    }

    public String grade(double average) {
        if (average > 90) {
            return "A";
        } else if (average > 75) {
            return "B";
        } else if (average > 60) {
            return "C";
        }
        return "F";
    }

    public boolean isPassing(int a, int b, int c) {
        return grade(average(a, b, c)) != "F";
    }
}
', NOW(6));
SET @version = LAST_INSERT_ID();
INSERT INTO coding_test_cases (problem_version_id, sort_order, name, concept, visible, input_spec, expected_spec) VALUES
 (@version, 0, 'The average keeps its fraction', 'integer division', TRUE, '{"script": ["new", "call average 70 70 71", "call average 80 85 90"]}', '{"trace": ["-", "70.33", "85.00"]}'),
 (@version, 1, 'Grades at the boundaries', 'boundary', TRUE, '{"script": ["new", "call grade 90", "call grade 75", "call grade 60"]}', '{"trace": ["-", "A", "B", "C"]}'),
 (@version, 2, 'A student with good marks passes', 'happy path', TRUE, '{"script": ["new", "call isPassing 80 70 90"]}', '{"trace": ["-", "true"]}'),
 (@version, 3, 'Grades just below each boundary', 'boundary', FALSE, '{"script": ["new", "call grade 89.99", "call grade 74.99", "call grade 59.99", "call grade 100", "call grade 0"]}', '{"trace": ["-", "B", "C", "F", "A", "F"]}'),
 (@version, 4, 'A low single mark fails the student', 'rule', FALSE, '{"script": ["new", "call isPassing 100 100 34", "call isPassing 100 100 35", "call isPassing 35 35 35"]}', '{"trace": ["-", "false", "true", "false"]}'),
 (@version, 5, 'The average decides the rest', 'rule', FALSE, '{"script": ["new", "call isPassing 60 60 60", "call isPassing 59 60 60", "call isPassing 0 0 0"]}', '{"trace": ["-", "true", "false", "false"]}'),
 (@version, 6, 'Averages that are not whole numbers', 'integer division', FALSE, '{"script": ["new", "call average 1 1 2", "call average 100 100 99", "call average 0 0 1"]}', '{"trace": ["-", "1.33", "99.67", "0.33"]}');

-- ---------------------------------------------------------------------------------------------------------------------------------
-- Request validation
INSERT INTO coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at)
SELECT id, 'request-validator', 'Request validation', 'EASY', 'java17', 'REQUEST_VALIDATOR', 1, TRUE, NOW(6), NOW(6) FROM topics WHERE slug = 'spring-boot-basics';
SET @problem = LAST_INSERT_ID();

INSERT INTO coding_problem_versions (problem_id, version, summary, statement_md, objectives_json, constraints_md, examples_json, starter_code, created_at)
VALUES (@problem, 1, 'Validate a sign-up request the way @Valid does: collect every field error, in field order, instead of stopping at the first one.',
 '## Request validation

In a Spring controller, `@Valid` on a request DTO checks field rules such as `@NotBlank`, `@Email` and `@Min`, and reports **every** broken field. Without Spring, you write the same logic by hand.

### Your task

```java
public class RequestValidator {
    public List<String> validate(String name, String email, int age)
}
```

It returns the list of errors, one text per problem, **in this field order: name, email, age**. A valid request gives an empty list.

### Rules

* **name:** `null` or only spaces gives `name:must not be blank`. Otherwise a name longer than 50 characters gives `name:too long`.
* **email:** `null` or only spaces gives `email:must not be blank`. Otherwise it is valid only if it has **exactly one `@`**, at least one character before the `@`, **no spaces**, and after the `@` a dot that is neither the first nor the last character. An invalid one gives `email:invalid format`.
* **age:** below 18 gives `age:must be at least 18`; above 120 gives `age:must be at most 120`.
* One field can give at most one error. Different fields give their errors together.

### In this exercise

This is plain Java with no framework, no database and no network. The tests build your object with `new` and call its public methods, so the behaviour is exactly what the rules below say.',
 '["Understand what bean validation does behind the annotations.", "Collect all errors, not just the first.", "Treat blank (only spaces) like missing.", "Check a format with simple string rules."]',
 '* In the tests `null` is a null string, `empty` is `""` and `_` is a space (so `_` alone is a blank name).
* An empty list is shown as `-`, and a list of errors as the errors joined by commas.
* Do not print anything and do not throw: every problem goes into the list.',
 '[{"title": "A valid request", "input": "validate(\\"Mohit\\", \\"mohit@example.com\\", 25)", "output": "(empty list)", "explanation": "Nothing is wrong, so nothing is reported."}, {"title": "Several problems at once", "input": "validate(\\" \\", \\"not-an-email\\", 12)", "output": "name:must not be blank, email:invalid format, age:must be at least 18", "explanation": "All three fields are wrong and all three errors are returned, in field order."}]',
 'import java.util.ArrayList;
import java.util.List;

public class RequestValidator {

    public List<String> validate(String name, String email, int age) {
        List<String> errors = new ArrayList<>();
        // TODO: add one error text per broken field, in the order name, email, age
        return errors;
    }
}
', NOW(6));
SET @version = LAST_INSERT_ID();
INSERT INTO coding_test_cases (problem_version_id, sort_order, name, concept, visible, input_spec, expected_spec) VALUES
 (@version, 0, 'A valid request has no errors', 'happy path', TRUE, '{"script": ["new", "call validate Mohit mohit@example.com 25"]}', '{"trace": ["-", "-"]}'),
 (@version, 1, 'Every broken field is reported', 'collecting', TRUE, '{"script": ["new", "call validate _ not-an-email 12"]}', '{"trace": ["-", "name:must not be blank,email:invalid format,age:must be at least 18"]}'),
 (@version, 2, 'A missing email', 'blank', TRUE, '{"script": ["new", "call validate Mohit null 30"]}', '{"trace": ["-", "email:must not be blank"]}'),
 (@version, 3, 'Blank means only spaces too', 'blank', FALSE, '{"script": ["new", "call validate empty a@b.com 30", "call validate null a@b.com 30", "call validate ___ a@b.com 30", "call validate Mo empty 30", "call validate Mo _ 30"]}', '{"trace": ["-", "name:must not be blank", "name:must not be blank", "name:must not be blank", "email:must not be blank", "email:must not be blank"]}'),
 (@version, 4, 'Name length limit', 'boundary', FALSE, '{"script": ["new", "call validate aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa a@b.com 30", "call validate aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa a@b.com 30"]}', '{"trace": ["-", "-", "name:too long"]}'),
 (@version, 5, 'Age limits', 'boundary', FALSE, '{"script": ["new", "call validate Mo a@b.com 17", "call validate Mo a@b.com 18", "call validate Mo a@b.com 120", "call validate Mo a@b.com 121", "call validate Mo a@b.com -3"]}', '{"trace": ["-", "age:must be at least 18", "-", "-", "age:must be at most 120", "age:must be at least 18"]}'),
 (@version, 6, 'Email format rules', 'format', FALSE, '{"script": ["new", "call validate Mo a@b 30", "call validate Mo @b.com 30", "call validate Mo a@@b.com 30", "call validate Mo a@b.c 30", "call validate Mo a@.com 30", "call validate Mo a@b. 30", "call validate Mo a_b@c.com 30", "call validate Mo abc 30", "call validate Mo a@b.c.d 30"]}', '{"trace": ["-", "email:invalid format", "email:invalid format", "email:invalid format", "-", "email:invalid format", "email:invalid format", "email:invalid format", "email:invalid format", "-"]}'),
 (@version, 7, 'Errors keep the field order', 'order', FALSE, '{"script": ["new", "call validate empty bad 200", "call validate empty a@b.com 200", "call validate Mo bad 5"]}', '{"trace": ["-", "name:must not be blank,email:invalid format,age:must be at most 120", "name:must not be blank,age:must be at most 120", "email:invalid format,age:must be at least 18"]}');

-- ---------------------------------------------------------------------------------------------------------------------------------
-- Product service (CRUD)
INSERT INTO coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at)
SELECT id, 'product-service-crud', 'Product service (CRUD)', 'MEDIUM', 'java17', 'PRODUCT_SERVICE', 1, TRUE, NOW(6), NOW(6) FROM topics WHERE slug = 'spring-boot-basics';
SET @problem = LAST_INSERT_ID();

INSERT INTO coding_problem_versions (problem_id, version, summary, statement_md, objectives_json, constraints_md, examples_json, starter_code, created_at)
VALUES (@problem, 1, 'Write the create, read, update and delete logic of a service over an in-memory store, with the duplicate and not-found rules a REST API needs.',
 '## Product service (CRUD)

A REST API for products needs a service layer: **create**, **read**, **update**, **delete**. Here the store is a plain `Map` in memory, so you can concentrate on the rules the controller depends on.

### Your task

```java
public class ProductService {
    public void create(int id, String name, double price)
    public String get(int id)                       // the product name
    public double priceOf(int id)
    public void update(int id, String name, double price)
    public void delete(int id)
    public int count()
    public List<String> names()                     // product names in ascending id order
}
```

`DuplicateProductException` and `ProductNotFoundException` are provided (unchecked exceptions with a `String` message constructor).

### Rules

* **Validation first.** `create` and `update` throw `IllegalArgumentException` when the id is 0 or less, the name is `null` or blank, or the price is 0 or less. This is checked **before** anything else.
* `create` throws `DuplicateProductException` when a product with that id already exists, and changes nothing.
* `get`, `priceOf`, `update` and `delete` throw `ProductNotFoundException` when no product has that id. `update` must **not** create a missing product.
* `delete` removes the product; afterwards `get` for that id throws `ProductNotFoundException`.
* `count` is the number of products. `names` lists their names ordered by id.

### In this exercise

This is plain Java with no framework, no database and no network. The tests build your object with `new` and call its public methods, so the behaviour is exactly what the rules below say.',
 '["Implement CRUD over a Map, the way a repository would store entities.", "Validate input before touching the store.", "Use the right exception for a duplicate and for a missing item.", "Keep a stable, sorted order for the results."]',
 '* In the tests a list is shown as its items joined by commas and an empty list as `-`; `_` stands for a space.
* The store lives inside the object: every test starts with a fresh, empty service.
* Do not print anything.',
 '[{"title": "Create and read", "input": "create(1, \\"Laptop\\", 55000); get(1)", "output": "Laptop", "explanation": "The product is stored under its id."}, {"title": "Create the same id twice", "input": "create(1, \\"Laptop\\", 55000); create(1, \\"Phone\\", 20000)", "output": "DuplicateProductException", "explanation": "The second call is refused and the first product is kept."}]',
 'import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProductService {

    private final Map<Integer, String> names = new HashMap<>();
    private final Map<Integer, Double> prices = new HashMap<>();

    public void create(int id, String name, double price) {
        // TODO
    }

    public String get(int id) {
        // TODO
        return null;
    }

    public double priceOf(int id) {
        // TODO
        return 0;
    }

    public void update(int id, String name, double price) {
        // TODO
    }

    public void delete(int id) {
        // TODO
    }

    public int count() {
        // TODO
        return 0;
    }

    public List<String> names() {
        // TODO: ordered by id
        return new ArrayList<>();
    }
}
', NOW(6));
SET @version = LAST_INSERT_ID();
INSERT INTO coding_test_cases (problem_version_id, sort_order, name, concept, visible, input_spec, expected_spec) VALUES
 (@version, 0, 'Create and read a product', 'create', TRUE, '{"script": ["new", "call create 1 Laptop 55000", "call get 1", "call priceOf 1", "call count"]}', '{"trace": ["-", "-", "Laptop", "55000.00", "1"]}'),
 (@version, 1, 'A duplicate id is refused', 'duplicate', TRUE, '{"script": ["new", "call create 1 Laptop 55000", "call create 1 Phone 20000", "call get 1"]}', '{"trace": ["-", "-", "EX:DuplicateProductException", "Laptop"]}'),
 (@version, 2, 'An unknown id is not found', 'not found', TRUE, '{"script": ["new", "call get 7", "call delete 7"]}', '{"trace": ["-", "EX:ProductNotFoundException", "EX:ProductNotFoundException"]}'),
 (@version, 3, 'Update changes name and price', 'update', FALSE, '{"script": ["new", "call create 1 Laptop 55000", "call update 1 Notebook 45000", "call get 1", "call priceOf 1", "call count"]}', '{"trace": ["-", "-", "-", "Notebook", "45000.00", "1"]}'),
 (@version, 4, 'Update never creates', 'update', FALSE, '{"script": ["new", "call update 9 Ghost 10", "call count", "call get 9"]}', '{"trace": ["-", "EX:ProductNotFoundException", "0", "EX:ProductNotFoundException"]}'),
 (@version, 5, 'Delete removes the product', 'delete', FALSE, '{"script": ["new", "call create 1 Laptop 55000", "call create 2 Phone 20000", "call delete 1", "call get 1", "call count", "call names"]}', '{"trace": ["-", "-", "-", "-", "EX:ProductNotFoundException", "1", "Phone"]}'),
 (@version, 6, 'Names follow the id order', 'ordering', FALSE, '{"script": ["new", "call create 30 Cable 100", "call create 10 Laptop 55000", "call create 20 Phone 20000", "call names"]}', '{"trace": ["-", "-", "-", "-", "Laptop,Phone,Cable"]}'),
 (@version, 7, 'Invalid input is rejected before anything else', 'validation', FALSE, '{"script": ["new", "call create 0 Laptop 100", "call create 1 empty 100", "call create 1 _ 100", "call create 1 null 100", "call create 1 Laptop 0", "call create 1 Laptop -5", "call count"]}', '{"trace": ["-", "EX:IllegalArgumentException", "EX:IllegalArgumentException", "EX:IllegalArgumentException", "EX:IllegalArgumentException", "EX:IllegalArgumentException", "EX:IllegalArgumentException", "0"]}'),
 (@version, 8, 'Validation comes before the existence check', 'order', FALSE, '{"script": ["new", "call update 5 empty 100", "call update 5 Laptop 0", "call update 5 Laptop 100"]}', '{"trace": ["-", "EX:IllegalArgumentException", "EX:IllegalArgumentException", "EX:ProductNotFoundException"]}'),
 (@version, 9, 'A deleted id can be created again', 'delete', FALSE, '{"script": ["new", "call create 1 Laptop 100", "call delete 1", "call create 1 Phone 200", "call get 1", "call delete 1", "call delete 1"]}', '{"trace": ["-", "-", "-", "-", "Phone", "-", "EX:ProductNotFoundException"]}');

-- ---------------------------------------------------------------------------------------------------------------------------------
-- Exception to HTTP status
INSERT INTO coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at)
SELECT id, 'exception-to-http-status', 'Exception to HTTP status', 'EASY', 'java17', 'ERROR_MAPPER', 1, TRUE, NOW(6), NOW(6) FROM topics WHERE slug = 'spring-boot-basics';
SET @problem = LAST_INSERT_ID();

INSERT INTO coding_problem_versions (problem_id, version, summary, statement_md, objectives_json, constraints_md, examples_json, starter_code, created_at)
VALUES (@problem, 1, 'Map exceptions to HTTP status codes and error bodies the way a @RestControllerAdvice does, without leaking internal messages.',
 '## Exception to HTTP status

A `@RestControllerAdvice` turns exceptions into HTTP responses. The rules are plain logic, so you can write them without Spring.

### Your task

```java
public class ErrorMapper {
    public int statusFor(String exceptionName)
    public String bodyFor(String exceptionName, String message)
}
```

`exceptionName` is the simple class name of the exception, for example `ProductNotFoundException`.

### Rules

**statusFor**

| Exception | Status |
|---|---|
| `IllegalArgumentException`, `MethodArgumentNotValidException` | 400 |
| `AuthenticationException` | 401 |
| `AccessDeniedException` | 403 |
| `ProductNotFoundException` | 404 |
| `DuplicateProductException` | 409 |
| anything else, `null` or empty | 500 |

**bodyFor** returns the text `<status> <reason>: <text>` where the reason is `Bad Request` (400), `Unauthorized` (401), `Forbidden` (403), `Not Found` (404), `Conflict` (409) or `Internal Server Error` (500), and:

* for status 500 the text is always `Unexpected error`. The real message is **never** shown, because it may reveal internal details;
* for any other status the text is the given message, but when the message is `null` or blank the text is the reason instead.

### In this exercise

This is plain Java with no framework, no database and no network. The tests build your object with `new` and call its public methods, so the behaviour is exactly what the rules below say.',
 '["Understand what a global exception handler decides: status code and body.", "Map each exception type to a suitable status.", "Never expose internal details of an unexpected error.", "Build a consistent error body."]',
 '* In the tests `null` is a null string, `empty` is `""` and `_` stands for a space.
* Names are case-sensitive.
* Do not print anything.',
 '[{"title": "A missing product", "input": "bodyFor(\\"ProductNotFoundException\\", \\"Product 7 not found\\")", "output": "404 Not Found: Product 7 not found", "explanation": "The message of a client error is safe to show."}, {"title": "An unexpected failure", "input": "bodyFor(\\"NullPointerException\\", \\"Cannot invoke ... at line 42\\")", "output": "500 Internal Server Error: Unexpected error", "explanation": "The internal message is hidden from the client."}]',
 'public class ErrorMapper {

    public int statusFor(String exceptionName) {
        // TODO
        return 0;
    }

    public String bodyFor(String exceptionName, String message) {
        // TODO
        return null;
    }
}
', NOW(6));
SET @version = LAST_INSERT_ID();
INSERT INTO coding_test_cases (problem_version_id, sort_order, name, concept, visible, input_spec, expected_spec) VALUES
 (@version, 0, 'A missing product is 404', 'mapping', TRUE, '{"script": ["new", "call statusFor ProductNotFoundException", "call bodyFor ProductNotFoundException Product_7_not_found"]}', '{"trace": ["-", "404", "404 Not Found: Product 7 not found"]}'),
 (@version, 1, 'Bad input is 400', 'mapping', TRUE, '{"script": ["new", "call statusFor IllegalArgumentException", "call statusFor MethodArgumentNotValidException"]}', '{"trace": ["-", "400", "400"]}'),
 (@version, 2, 'An unexpected error hides its message', 'security', TRUE, '{"script": ["new", "call bodyFor NullPointerException Cannot_invoke_at_line_42"]}', '{"trace": ["-", "500 Internal Server Error: Unexpected error"]}'),
 (@version, 3, 'Every mapped exception', 'mapping', FALSE, '{"script": ["new", "call statusFor AuthenticationException", "call statusFor AccessDeniedException", "call statusFor DuplicateProductException", "call statusFor RuntimeException"]}', '{"trace": ["-", "401", "403", "409", "500"]}'),
 (@version, 4, 'Unknown, null and empty names are 500', 'default', FALSE, '{"script": ["new", "call statusFor null", "call statusFor empty", "call statusFor Whatever", "call statusFor illegalargumentexception"]}', '{"trace": ["-", "500", "500", "500", "500"]}'),
 (@version, 5, 'Bodies of the client errors', 'body', FALSE, '{"script": ["new", "call bodyFor AuthenticationException Bad_token", "call bodyFor AccessDeniedException Admins_only", "call bodyFor DuplicateProductException Product_1_exists", "call bodyFor IllegalArgumentException Price_must_be_positive"]}', '{"trace": ["-", "401 Unauthorized: Bad token", "403 Forbidden: Admins only", "409 Conflict: Product 1 exists", "400 Bad Request: Price must be positive"]}'),
 (@version, 6, 'A blank message falls back to the reason', 'body', FALSE, '{"script": ["new", "call bodyFor ProductNotFoundException null", "call bodyFor ProductNotFoundException empty", "call bodyFor AccessDeniedException _"]}', '{"trace": ["-", "404 Not Found: Not Found", "404 Not Found: Not Found", "403 Forbidden: Forbidden"]}'),
 (@version, 7, 'A 500 never shows the message, whatever it is', 'security', FALSE, '{"script": ["new", "call bodyFor null Secret_password", "call bodyFor empty empty", "call bodyFor Whatever null"]}', '{"trace": ["-", "500 Internal Server Error: Unexpected error", "500 Internal Server Error: Unexpected error", "500 Internal Server Error: Unexpected error"]}');

-- ---------------------------------------------------------------------------------------------------------------------------------
-- Bean registry: @Primary and @Qualifier
INSERT INTO coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at)
SELECT id, 'bean-registry-primary', 'Bean registry: @Primary and @Qualifier', 'MEDIUM', 'java17', 'BEAN_REGISTRY', 1, TRUE, NOW(6), NOW(6) FROM topics WHERE slug = 'spring-boot-basics';
SET @problem = LAST_INSERT_ID();

INSERT INTO coding_problem_versions (problem_id, version, summary, statement_md, objectives_json, constraints_md, examples_json, starter_code, created_at)
VALUES (@problem, 1, 'Write a tiny container that resolves a bean by type, prefers the primary one when several match, and fails clearly when it cannot decide.',
 '## Bean registry: @Primary and @Qualifier

When two beans share a type, Spring cannot inject "the" bean unless one is marked `@Primary` or the injection point names one with `@Qualifier`. You write a miniature of that decision.

### Your task

```java
public class BeanRegistry {
    public void register(String type, String name, boolean primary)
    public String resolve(String type)                  // like injecting by type
    public String resolve(String type, String name)     // like @Qualifier("name")
    public int countOf(String type)
}
```

`NoSuchBeanException` and `NoUniqueBeanException` are provided (unchecked exceptions with a `String` message constructor).

### Rules

* `register` stores a bean (its **type**, its **name** and whether it is primary).
    * A bean **name** must be unique in the whole registry: registering a name that exists throws `IllegalStateException`.
    * Only **one** bean per type may be primary: a second primary of the same type throws `IllegalStateException`.
    * A rejected registration stores nothing.
* `resolve(type)`:
    * no bean of that type: `NoSuchBeanException`;
    * exactly one bean of that type: its name;
    * several: the name of the primary one if there is exactly one primary, otherwise `NoUniqueBeanException`.
* `resolve(type, name)` returns that name when a bean of **that type** has that name, otherwise throws `NoSuchBeanException` (a bean of another type with the same name does not count).
* `countOf(type)` is the number of beans of that type.

### In this exercise

This is plain Java with no framework, no database and no network. The tests build your object with `new` and call its public methods, so the behaviour is exactly what the rules below say.',
 '["Understand how Spring chooses between several beans of the same type.", "Model @Primary and @Qualifier with plain data.", "Fail with the right exception when nothing matches or the choice is ambiguous.", "Keep a registry consistent by rejecting contradictory registrations."]',
 '* In the tests the arguments of `register` are written `type name primary`, for example `register PaymentService card true`.
* Types and names are case-sensitive.
* Do not print anything.',
 '[{"title": "The primary bean wins", "input": "register(PaymentService, card, true); register(PaymentService, upi, false); resolve(PaymentService)", "output": "card", "explanation": "Two candidates, one is primary."}, {"title": "Ambiguous without a primary", "input": "register(Sender, sms, false); register(Sender, mail, false); resolve(Sender)", "output": "NoUniqueBeanException", "explanation": "Spring would refuse to start; here the call fails."}]',
 'import java.util.ArrayList;
import java.util.List;

public class BeanRegistry {

    private final List<String[]> beans = new ArrayList<>();   // each entry: {type, name, "true" or "false" for primary}

    public void register(String type, String name, boolean primary) {
        // TODO
    }

    public String resolve(String type) {
        // TODO
        return null;
    }

    public String resolve(String type, String name) {
        // TODO
        return null;
    }

    public int countOf(String type) {
        // TODO
        return 0;
    }
}
', NOW(6));
SET @version = LAST_INSERT_ID();
INSERT INTO coding_test_cases (problem_version_id, sort_order, name, concept, visible, input_spec, expected_spec) VALUES
 (@version, 0, 'A single bean is resolved by type', 'resolve', TRUE, '{"script": ["new", "call register PaymentService card true", "call resolve PaymentService", "call countOf PaymentService"]}', '{"trace": ["-", "-", "card", "1"]}'),
 (@version, 1, 'The primary bean wins among several', 'primary', TRUE, '{"script": ["new", "call register PaymentService card true", "call register PaymentService upi false", "call resolve PaymentService"]}', '{"trace": ["-", "-", "-", "card"]}'),
 (@version, 2, 'Ambiguity without a primary fails', 'ambiguity', TRUE, '{"script": ["new", "call register Sender sms false", "call register Sender mail false", "call resolve Sender"]}', '{"trace": ["-", "-", "-", "EX:NoUniqueBeanException"]}'),
 (@version, 3, 'An unknown type has no bean', 'missing', FALSE, '{"script": ["new", "call resolve Nothing", "call countOf Nothing", "call register A a false", "call resolve B"]}', '{"trace": ["-", "EX:NoSuchBeanException", "0", "-", "EX:NoSuchBeanException"]}'),
 (@version, 4, 'A qualifier chooses by name', 'qualifier', FALSE, '{"script": ["new", "call register Sender sms false", "call register Sender mail false", "call resolve Sender mail", "call resolve Sender sms", "call resolve Sender fax"]}', '{"trace": ["-", "-", "-", "mail", "sms", "EX:NoSuchBeanException"]}'),
 (@version, 5, 'A qualifier needs the right type', 'qualifier', FALSE, '{"script": ["new", "call register Sender sms false", "call register Logger audit false", "call resolve Logger sms", "call resolve Logger audit"]}', '{"trace": ["-", "-", "-", "EX:NoSuchBeanException", "audit"]}'),
 (@version, 6, 'Two primaries of one type are refused', 'consistency', FALSE, '{"script": ["new", "call register Sender sms true", "call register Sender mail true", "call countOf Sender", "call resolve Sender"]}', '{"trace": ["-", "-", "EX:IllegalStateException", "1", "sms"]}'),
 (@version, 7, 'Primaries of different types are fine', 'consistency', FALSE, '{"script": ["new", "call register Sender sms true", "call register Logger audit true", "call resolve Sender", "call resolve Logger"]}', '{"trace": ["-", "-", "-", "sms", "audit"]}'),
 (@version, 8, 'A bean name is unique across types', 'consistency', FALSE, '{"script": ["new", "call register Sender main false", "call register Logger main false", "call countOf Logger", "call register Sender main false"]}', '{"trace": ["-", "-", "EX:IllegalStateException", "0", "EX:IllegalStateException"]}'),
 (@version, 9, 'A refused registration leaves the registry as it was', 'consistency', FALSE, '{"script": ["new", "call register Sender sms true", "call register Sender mail true", "call register Sender fax false", "call countOf Sender", "call resolve Sender"]}', '{"trace": ["-", "-", "EX:IllegalStateException", "-", "2", "sms"]}'),
 (@version, 10, 'Several primaries are impossible, several non-primaries are ambiguous', 'ambiguity', FALSE, '{"script": ["new", "call register Sender a false", "call register Sender b false", "call register Sender c true", "call resolve Sender"]}', '{"trace": ["-", "-", "-", "-", "c"]}');
