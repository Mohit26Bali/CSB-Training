/** Provided to the learner: the final state of a saga run. */
public enum SagaStatus {
    /** Every step executed. */
    COMPLETED,
    /** A step failed and every earlier step was compensated. */
    COMPENSATED,
    /** A step failed and at least one compensation also failed: manual recovery is needed for those steps. */
    COMPENSATION_FAILED
}
