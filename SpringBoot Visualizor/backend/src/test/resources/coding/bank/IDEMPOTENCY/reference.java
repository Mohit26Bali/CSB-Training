import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

public final class IdempotencyHandler {

    private record Entry(Object value, long completedAt) {
    }

    private final long ttlMillis;
    private final Clock clock;
    private final Map<String, Entry> completed = new HashMap<>();
    private final Set<String> running = new HashSet<>();

    public IdempotencyHandler(long ttlMillis, Clock clock) {
        if (ttlMillis < 1) {
            throw new IllegalArgumentException("ttlMillis must be at least 1");
        }
        this.ttlMillis = ttlMillis;
        this.clock = clock;
    }

    @SuppressWarnings("unchecked")
    public <T> T handle(String key, Callable<T> operation) throws Exception {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("key must not be empty");
        }
        synchronized (this) {
            Entry stored = completed.get(key);
            if (stored != null) {
                if (clock.nowMillis() - stored.completedAt() < ttlMillis) {
                    return (T) stored.value();
                }
                completed.remove(key); // expired: the key is forgotten
            }
            if (!running.add(key)) {
                throw new RequestInProgressException("a request with key " + key + " is already running");
            }
        }
        T result;
        try {
            result = operation.call();
        } catch (Exception | Error failure) {
            synchronized (this) {
                running.remove(key); // a failure is not remembered: the same key may be retried
            }
            throw failure;
        }
        synchronized (this) {
            completed.put(key, new Entry(result, clock.nowMillis()));
            running.remove(key);
        }
        return result;
    }
}
