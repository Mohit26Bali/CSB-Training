import java.util.ArrayList;
import java.util.List;

public final class SagaCoordinator {

    private final List<SagaStep> steps;
    private final List<String> pending = new ArrayList<>();
    private boolean ran;

    public SagaCoordinator(List<SagaStep> steps) {
        this.steps = new ArrayList<>(steps);
    }

    public SagaStatus run() {
        if (ran) {
            throw new IllegalStateException("a saga can run only once");
        }
        ran = true;
        List<SagaStep> completed = new ArrayList<>();
        boolean failed = false;
        for (SagaStep step : steps) {
            try {
                step.execute();
                completed.add(step);
            } catch (Exception failure) {
                failed = true;
            }
        }
        return failed ? compensate(completed) : SagaStatus.COMPLETED;
    }

    private SagaStatus compensate(List<SagaStep> completed) {
        for (int i = completed.size() - 1; i >= 0; i--) {
            try {
                completed.get(i).compensate();
            } catch (Exception failure) {
                pending.add(completed.get(i).name()); // not retried: reported for manual recovery
            }
        }
        return pending.isEmpty() ? SagaStatus.COMPENSATED : SagaStatus.COMPENSATION_FAILED;
    }

    public List<String> pendingCompensations() {
        return List.copyOf(pending);
    }
}
