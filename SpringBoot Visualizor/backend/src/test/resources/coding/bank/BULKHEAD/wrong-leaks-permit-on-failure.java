import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;

public final class Bulkhead {

    private final int maxConcurrent;
    private final Semaphore permits;

    public Bulkhead(int maxConcurrent) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("maxConcurrent must be at least 1");
        }
        this.maxConcurrent = maxConcurrent;
        this.permits = new Semaphore(maxConcurrent);
    }

    public <T> T execute(Callable<T> operation) throws Exception {
        if (!permits.tryAcquire()) {
            throw new BulkheadFullException("bulkhead is full");
        }
        T result = operation.call();
        permits.release();
        return result;
    }

    public int inFlight() {
        return maxConcurrent - permits.availablePermits();
    }

    public int availablePermits() {
        return permits.availablePermits();
    }
}
