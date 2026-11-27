package com.springlab.lab.jdbc;

import java.util.ArrayList;
import java.util.List;

/** The ordered JDBC observations of ONE experiment run. Never shared between runs. */
public final class JdbcEventLog {

    private final List<JdbcEvent> events = new ArrayList<>();

    public synchronized void add(String connection, String kind, String sql, String detail) {
        events.add(new JdbcEvent(events.size(), connection, kind, sql, detail));
    }

    public synchronized int size() {
        return events.size();
    }

    public synchronized List<JdbcEvent> since(int from) {
        return List.copyOf(events.subList(from, events.size()));
    }
}
