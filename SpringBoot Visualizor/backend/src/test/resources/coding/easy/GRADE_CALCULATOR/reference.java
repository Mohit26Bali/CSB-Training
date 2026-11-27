public class GradeCalculator {

    public double average(int a, int b, int c) {
        return (a + b + c) / 3.0;
    }

    public String grade(double average) {
        if (average >= 90) {
            return "A";
        } else if (average >= 75) {
            return "B";
        } else if (average >= 60) {
            return "C";
        }
        return "F";
    }

    public boolean isPassing(int a, int b, int c) {
        return average(a, b, c) >= 60 && Math.min(a, Math.min(b, c)) >= 35;
    }
}
