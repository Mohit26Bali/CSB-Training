package com.springlab.lab.micro.core;

/**
 * The logical clock of one run. Latency and timeouts are SIMULATED by advancing this clock, never by sleeping, so every run is
 * deterministic and fast and one user's run cannot slow another's. Durations derived from it are labelled SIMULATED in the UI.
 */
public final class SimClock {

    private long now;

    public long now() {
        return now;
    }

    public void advanceTo(long millis) {
        if (millis > now) {
            now = millis;
        }
    }
}
