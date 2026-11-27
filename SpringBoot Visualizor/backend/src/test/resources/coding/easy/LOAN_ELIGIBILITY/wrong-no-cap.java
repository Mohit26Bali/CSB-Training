public class LoanEligibility {

    public double maxLoan(double monthlyIncome) {
        return monthlyIncome * 20;
    }

    public String decide(int age, double monthlyIncome, int creditScore, double requestedAmount) {
        if (age < 21 || age > 60) {
            return "REJECTED:AGE";
        }
        if (creditScore < 600) {
            return "REJECTED:CREDIT";
        }
        if (monthlyIncome < 25000) {
            return "REJECTED:INCOME";
        }
        if (requestedAmount > maxLoan(monthlyIncome)) {
            return "REJECTED:AMOUNT";
        }
        if (creditScore < 700) {
            return "APPROVED:MANUAL_REVIEW";
        }
        return "APPROVED";
    }
}
