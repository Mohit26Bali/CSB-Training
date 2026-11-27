import java.util.ArrayList;
import java.util.List;

public class BeanRegistry {

    private final List<String[]> beans = new ArrayList<>();   // {type, name, "true" or "false" for primary}

    public void register(String type, String name, boolean primary) {
        for (String[] b : beans) {
            if (b[0].equals(type) && b[1].equals(name)) {
                throw new IllegalStateException("bean name already registered");
            }
            if (primary && b[0].equals(type) && b[2].equals("true")) {
                throw new IllegalStateException("a primary bean already exists for this type");
            }
        }
        beans.add(new String[]{type, name, String.valueOf(primary)});
    }

    private List<String[]> of(String type) {
        List<String[]> found = new ArrayList<>();
        for (String[] b : beans) {
            if (b[0].equals(type)) {
                found.add(b);
            }
        }
        return found;
    }

    public String resolve(String type) {
        List<String[]> found = of(type);
        if (found.isEmpty()) {
            throw new NoSuchBeanException("no bean of type " + type);
        }
        if (found.size() == 1) {
            return found.get(0)[1];
        }
        String primary = null;
        int primaries = 0;
        for (String[] b : found) {
            if (b[2].equals("true")) {
                primary = b[1];
                primaries++;
            }
        }
        if (primaries == 1) {
            return primary;
        }
        throw new NoUniqueBeanException("several beans of type " + type + " and no primary");
    }

    public String resolve(String type, String name) {
        for (String[] b : of(type)) {
            if (b[1].equals(name)) {
                return b[1];
            }
        }
        throw new NoSuchBeanException("no bean " + name + " of type " + type);
    }

    public int countOf(String type) {
        return of(type).size();
    }
}
