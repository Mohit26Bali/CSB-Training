/** The adapters the sandbox runtime knows, by harness kind. The kind comes from the trusted driver, never from the learner. */
final class Adapters {

    private Adapters() {
    }

    static Adapter of(String kind) {
        switch (kind) {
            case "CIRCUIT_BREAKER":
                return new CircuitBreakerAdapter();
            case "BULKHEAD":
                return new BulkheadAdapter();
            case "IDEMPOTENCY":
                return new IdempotencyAdapter();
            case "SAGA":
                return new SagaAdapter();
            case "OUTBOX":
                return new OutboxAdapter();
            default:
                return reflective(kind);
        }
    }

    private static ReflectiveAdapter reflective(String kind) {
        switch (kind) {
            case "PASSWORD_VALIDATOR":
                return new ReflectiveAdapter("PasswordValidator", new String[]{""}, new String[]{"validate(String)->void", "firstError(String)->String"});
            case "LOAN_ELIGIBILITY":
                return new ReflectiveAdapter("LoanEligibility", new String[]{""}, new String[]{"decide(int,double,int,double)->String", "maxLoan(double)->double"});
            case "SALARY_CALCULATOR":
                return new ReflectiveAdapter("SalaryCalculator", new String[]{""}, new String[]{"netSalary(String,double,int)->double"});
            case "BANK_ACCOUNT":
                return new ReflectiveAdapter("BankAccount", new String[]{"String,double"},
                        new String[]{"deposit(double)->void", "withdraw(double)->void", "getBalance()->double", "getOwner()->String", "transactionCount()->int"});
            case "TRAINING_REGISTRY":
                return new ReflectiveAdapter("TrainingRegistry", new String[]{""},
                        new String[]{"register(String,String)->boolean", "countFor(String)->int", "technologiesOf(String)->List", "participantsIn(String)->List"});
            case "GRADE_CALCULATOR":
                return new ReflectiveAdapter("GradeCalculator", new String[]{""}, new String[]{"average(int,int,int)->double", "grade(double)->String", "isPassing(int,int,int)->boolean"});
            case "REQUEST_VALIDATOR":
                return new ReflectiveAdapter("RequestValidator", new String[]{""}, new String[]{"validate(String,String,int)->List"});
            case "PRODUCT_SERVICE":
                return new ReflectiveAdapter("ProductService", new String[]{""},
                        new String[]{"create(int,String,double)->void", "get(int)->String", "priceOf(int)->double", "update(int,String,double)->void", "delete(int)->void", "count()->int", "names()->List"});
            case "ERROR_MAPPER":
                return new ReflectiveAdapter("ErrorMapper", new String[]{""}, new String[]{"statusFor(String)->int", "bodyFor(String,String)->String"});
            case "BEAN_REGISTRY":
                return new ReflectiveAdapter("BeanRegistry", new String[]{""}, new String[]{"register(String,String,boolean)->void", "resolve(String)->String", "resolve(String,String)->String", "countOf(String)->int"});
            default:
                return null;
        }
    }
}
