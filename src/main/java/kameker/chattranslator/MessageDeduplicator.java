package kameker.chattranslator;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class MessageDeduplicator {
    private final ConcurrentMap<String, Long> timestamps = new ConcurrentHashMap<>();

    boolean markIfNew(String key, long now, long timeoutMs) {
        java.util.concurrent.atomic.AtomicBoolean added = new java.util.concurrent.atomic.AtomicBoolean();
        timestamps.compute(key, (ignored, timestamp) -> {
            if (timestamp == null || now - timestamp > timeoutMs) {
                added.set(true);
                return now;
            }
            return timestamp;
        });
        return added.get();
    }

    void remove(String key) {
        timestamps.remove(key);
    }

    void clean(long now, long timeoutMs) {
        timestamps.entrySet().removeIf(entry -> now - entry.getValue() > timeoutMs);
    }

    int size() {
        return timestamps.size();
    }
}
