package judge;

/** Resource limits for one judged run. The cgroup limits are enforced by the kernel; the others by the driver. */
public record Limits(
        long totalWallMillis,
        long startupMillis,
        long perTestMillis,
        long outputCapBytes,
        int sourceMaxBytes,
        String memoryMax,
        int tasksMax,
        int cpuQuotaPercent,
        int heapMb) {

    public static Limits defaults() {
        return new Limits(20_000, 12_000, 3_000, 64 * 1024, 64 * 1024, "384M", 64, 100, 128);
    }

    public Limits withTasksMax(int n) {
        return new Limits(totalWallMillis, startupMillis, perTestMillis, outputCapBytes, sourceMaxBytes, memoryMax, n, cpuQuotaPercent, heapMb);
    }

    public Limits withMemoryMax(String m) {
        return new Limits(totalWallMillis, startupMillis, perTestMillis, outputCapBytes, sourceMaxBytes, m, tasksMax, cpuQuotaPercent, heapMb);
    }

    public Limits withTimes(long total, long startup, long perTest) {
        return new Limits(total, startup, perTest, outputCapBytes, sourceMaxBytes, memoryMax, tasksMax, cpuQuotaPercent, heapMb);
    }
}
