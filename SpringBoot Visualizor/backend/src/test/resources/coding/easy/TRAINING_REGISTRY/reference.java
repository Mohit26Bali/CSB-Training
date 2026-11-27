import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrainingRegistry {

    private final Map<String, List<String>> byTechnology = new HashMap<>();

    public boolean register(String participant, String technology) {
        if (participant == null || participant.trim().isEmpty() || technology == null || technology.trim().isEmpty()) {
            throw new IllegalArgumentException("participant and technology are required");
        }
        List<String> people = byTechnology.computeIfAbsent(technology, k -> new ArrayList<>());
        if (people.contains(participant)) {
            return false;
        }
        if (people.size() >= 3) {
            throw new IllegalStateException("the class is full");
        }
        people.add(participant);
        return true;
    }

    public int countFor(String technology) {
        return byTechnology.getOrDefault(technology, new ArrayList<>()).size();
    }

    public List<String> technologiesOf(String participant) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : byTechnology.entrySet()) {
            if (e.getValue().contains(participant)) {
                result.add(e.getKey());
            }
        }
        Collections.sort(result);
        return result;
    }

    public List<String> participantsIn(String technology) {
        return new ArrayList<>(byTechnology.getOrDefault(technology, new ArrayList<>()));
    }
}
