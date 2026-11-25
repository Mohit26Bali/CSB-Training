package demo.cache;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class PriceService {
    private final AtomicInteger lookups = new AtomicInteger();   // how often the slow method really ran

    public int lookups() { return lookups.get(); }               // read it through a METHOD: a field on a proxy is not the real one
    public void resetLookups() { lookups.set(0); }

    @Cacheable(cacheNames = "prices", key = "#sku", unless = "#result < 0")
    public int priceOf(String sku) {
        lookups.incrementAndGet();                               // pretend this is a slow database call
        return sku.length() * 100;
    }

    @CachePut(cacheNames = "prices", key = "#sku")               // always runs, and refreshes the cache
    public int updatePrice(String sku, int newPrice) {
        return newPrice;
    }

    @CacheEvict(cacheNames = "prices", key = "#sku")
    public void discontinue(String sku) { }

    @CacheEvict(cacheNames = "prices", allEntries = true)
    public void clearAll() { }

    public int cachedViaSelfCall(String sku) {
        return priceOf(sku);                                     // self-invocation bypasses the cache proxy
    }
}
