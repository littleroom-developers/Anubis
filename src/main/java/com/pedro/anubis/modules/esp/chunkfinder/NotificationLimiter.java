package com.pedro.anubis.modules.esp.chunkfinder;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NotificationLimiter<K> {
    private final int maxPerMinute;
    private final long cooldownMs;
    private final Map<K, Long> lastByKey = new ConcurrentHashMap<>();
    private final Queue<Long> recent = new ConcurrentLinkedQueue<>();

    public NotificationLimiter(int maxPerMinute, long cooldownMs) {
        this.maxPerMinute = Math.max(1, maxPerMinute);
        this.cooldownMs = Math.max(0L, cooldownMs);
    }

    public boolean shouldNotify(K key, long nowMs) {
        cleanup(nowMs);

        Long last = lastByKey.get(key);
        if (last != null && nowMs - last < cooldownMs) return false;
        if (recent.size() >= maxPerMinute) return false;

        recent.offer(nowMs);
        lastByKey.put(key, nowMs);
        return true;
    }

    public void clear() {
        lastByKey.clear();
        recent.clear();
    }

    private void cleanup(long nowMs) {
        while (!recent.isEmpty()) {
            Long timestamp = recent.peek();
            if (timestamp == null || nowMs - timestamp <= 60_000L) break;
            recent.poll();
        }
    }
}
