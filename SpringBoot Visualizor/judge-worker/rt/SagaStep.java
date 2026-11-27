/** Provided to the learner: one step of a saga, with the action that undoes it. */
public interface SagaStep {
    String name();

    void execute() throws Exception;

    void compensate() throws Exception;
}
