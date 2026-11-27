import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Locale;

/**
 * One generic adapter for the simple, everyday exercises (validators, calculators, small services): the script builds the learner's object
 * and calls its public methods with typed arguments, and the adapter reports what each call returned or threw.
 *
 * Operations: {@code new ARG...} builds the object with the constructor that takes that many arguments; {@code call METHOD ARG...} calls a
 * public method the learner's class declares itself (never inherited methods such as getClass). Arguments are converted to the parameter's
 * type: int, long, double, boolean, char, String ("empty" is the empty string, "null" is null, "_" is a space).
 *
 * Observations: the returned value as one line (a double with two decimals, "empty" for the empty string, a collection or array as its
 * elements joined by a comma, "-" for void or an empty collection), or EX:SimpleName of the exception the call threw.
 *
 * The adapter is generic, but each exercise gives it a FIXED contract (class name, constructors and methods), checked before anything runs.
 */
final class ReflectiveAdapter extends Adapter {

    private final String className;
    private final String[] constructors;
    private final String[] methods;
    private Class<?> type;
    private Object instance;

    /**
     * @param constructors parameter lists such as "" or "int,String" (comma separated simple type names)
     * @param methods      signatures such as "validate(String)->String" ("->" is followed by the simple name of the return type)
     */
    ReflectiveAdapter(String className, String[] constructors, String[] methods) {
        this.className = className;
        this.constructors = constructors;
        this.methods = methods;
    }

    @Override
    String className() {
        return className;
    }

    @Override
    String check(Class<?> learner) {
        StringBuilder missing = new StringBuilder();
        for (String c : constructors) {
            if (findConstructor(learner, split(c)) == null) {
                missing.append("\n  public ").append(className).append('(').append(c.replace(",", ", ")).append(')');
            }
        }
        for (String m : methods) {
            String name = m.substring(0, m.indexOf('('));
            String[] params = split(m.substring(m.indexOf('(') + 1, m.indexOf(')')));
            String ret = m.substring(m.indexOf("->") + 2);
            Method found = findMethod(learner, name, params);
            if (found == null || !found.getReturnType().getSimpleName().equals(ret)) {
                missing.append("\n  public ").append(ret).append(' ').append(name).append('(').append(String.join(", ", params)).append(')');
            }
        }
        return missing.length() == 0 ? null : className + " must declare these public members:" + missing;
    }

    @Override
    void begin(Class<?> learner) {
        type = learner;
        instance = null;
    }

    @Override
    String step(String[] t) throws Exception {
        switch (t[0]) {
            case "new": {
                instance = null;
                Constructor<?> c = null;
                for (Constructor<?> k : type.getConstructors()) {
                    if (k.getParameterCount() == t.length - 1) {
                        c = k;
                        break;
                    }
                }
                if (c == null) {
                    throw new BadScript();
                }
                instance = c.newInstance(convert(c.getParameterTypes(), t, 1));
                return "-";
            }
            case "call": {
                String name = need(t, 2);
                Method m = null;
                for (Method k : type.getDeclaredMethods()) {
                    if (k.getName().equals(name) && Modifier.isPublic(k.getModifiers()) && !k.isSynthetic() && k.getParameterCount() == t.length - 2) {
                        m = k;
                        break;
                    }
                }
                if (m == null) {
                    throw new NoSuchMethodException(name);
                }
                if (instance == null && !Modifier.isStatic(m.getModifiers())) {
                    throw new IllegalStateException("no object: the script must say 'new' first");
                }
                Object result = m.invoke(Modifier.isStatic(m.getModifiers()) ? null : instance, convert(m.getParameterTypes(), t, 2));
                return m.getReturnType() == void.class ? "-" : format(result);
            }
            default:
                throw new BadScript();
        }
    }

    private static String[] split(String list) {
        return list.isEmpty() ? new String[0] : list.split(",");
    }

    private static Constructor<?> findConstructor(Class<?> c, String[] params) {
        for (Constructor<?> k : c.getConstructors()) {
            if (sameTypes(k.getParameterTypes(), params)) {
                return k;
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> c, String name, String[] params) {
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name) && Modifier.isPublic(m.getModifiers()) && sameTypes(m.getParameterTypes(), params)) {
                return m;
            }
        }
        return null;
    }

    private static boolean sameTypes(Class<?>[] actual, String[] wanted) {
        if (actual.length != wanted.length) {
            return false;
        }
        for (int i = 0; i < actual.length; i++) {
            if (!actual[i].getSimpleName().equals(wanted[i])) {
                return false;
            }
        }
        return true;
    }

    private static Object[] convert(Class<?>[] types, String[] tokens, int offset) {
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            String s = tokens[offset + i];
            Class<?> p = types[i];
            try {
                if (p == int.class) {
                    args[i] = Integer.parseInt(s);
                } else if (p == long.class) {
                    args[i] = Long.parseLong(s);
                } else if (p == double.class) {
                    args[i] = Double.parseDouble(s);
                } else if (p == boolean.class) {
                    if (!s.equals("true") && !s.equals("false")) {
                        throw new BadScript();
                    }
                    args[i] = Boolean.parseBoolean(s);
                } else if (p == char.class) {
                    if (s.length() != 1) {
                        throw new BadScript();
                    }
                    args[i] = s.charAt(0);
                } else if (p == String.class) {
                    args[i] = s.equals("empty") ? "" : s.equals("null") ? null : s.replace('_', ' ');
                } else {
                    throw new BadScript();
                }
            } catch (NumberFormatException e) {
                throw new BadScript();
            }
        }
        return args;
    }

    static String format(Object o) {
        if (o == null) {
            return "null";
        }
        if (o instanceof String) {
            String s = (String) o;
            return s.isEmpty() ? "empty" : s;
        }
        if (o instanceof Double || o instanceof Float) {
            return String.format(Locale.ROOT, "%.2f", ((Number) o).doubleValue());
        }
        if (o instanceof Enum) {
            return ((Enum<?>) o).name();
        }
        if (o instanceof Collection) {
            Collection<?> c = (Collection<?>) o;
            if (c.isEmpty()) {
                return "-";
            }
            StringBuilder sb = new StringBuilder();
            for (Object e : c) {
                sb.append(sb.length() == 0 ? "" : ",").append(format(e));
            }
            return sb.toString();
        }
        if (o.getClass().isArray()) {
            int n = Array.getLength(o);
            if (n == 0) {
                return "-";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) {
                sb.append(i == 0 ? "" : ",").append(format(Array.get(o, i)));
            }
            return sb.toString();
        }
        return String.valueOf(o);
    }
}
