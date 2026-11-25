package lib;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/** Hibernate calls this for every SQL statement it is about to prepare, so tests can see (and count) the real SQL. */
public class SqlRecorder implements StatementInspector {

    private static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        STATEMENTS.add(sql);
        return sql;
    }

    public static void clear() { STATEMENTS.clear(); }

    public static List<String> all() { return List.copyOf(STATEMENTS); }

    public static long count(String startsWith) {
        return STATEMENTS.stream().filter(s -> s.toLowerCase().startsWith(startsWith.toLowerCase())).count();
    }

    public static long selects() { return count("select"); }
}
