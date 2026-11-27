public class SalaryCalculator {

    public double netSalary(String type, double baseSalary, int daysWorked) {
        if (type == null || baseSalary < 0 || daysWorked < 0 || daysWorked > 30) {
            throw new IllegalArgumentException("invalid input");
        }
        switch (type) {
            case "PERMANENT": {
                double gross = baseSalary * 1.2;
                double rate = gross > 80000 ? 0.20 : gross > 40000 ? 0.10 : 0.0;
                return gross - gross * rate;
            }
            case "CONTRACT":
                return baseSalary / 30 * daysWorked * 0.95;
            case "INTERN":
                return Math.min(baseSalary, 15000);
            default:
                throw new IllegalArgumentException("unknown employee type");
        }
    }
}
