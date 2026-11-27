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
        return grade(average(a, b, c)) != "F";
    }
}
