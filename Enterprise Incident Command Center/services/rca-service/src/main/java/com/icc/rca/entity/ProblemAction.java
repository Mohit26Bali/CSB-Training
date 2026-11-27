package com.icc.rca.entity;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The buttons of a problem and the status each one may be pressed in (D31). This is the one place
 * that says which move is allowed from where; the service and the screen both read it.
 *
 * <pre>
 *  ASSESS          NEW
 *  CONFIRM         ASSESS
 *  FIX             ROOT_CAUSE_ANALYSIS
 *  RESOLVE         FIX_IN_PROGRESS
 *  COMPLETE        RESOLVED
 *  ACCEPT_RISK     ROOT_CAUSE_ANALYSIS, FIX_IN_PROGRESS     (closes the problem)
 *  MARK_DUPLICATE  ASSESS, ROOT_CAUSE_ANALYSIS              (closes the problem)
 *  CANCEL          ASSESS, ROOT_CAUSE_ANALYSIS              (closes the problem)
 *  RE_ANALYZE      FIX_IN_PROGRESS, RESOLVED, CLOSED        (back to ROOT_CAUSE_ANALYSIS)
 * </pre>
 */
public enum ProblemAction {
    ASSESS(ProblemStatus.NEW),
    CONFIRM(ProblemStatus.ASSESS),
    FIX(ProblemStatus.ROOT_CAUSE_ANALYSIS),
    RESOLVE(ProblemStatus.FIX_IN_PROGRESS),
    COMPLETE(ProblemStatus.RESOLVED),
    ACCEPT_RISK(ProblemStatus.ROOT_CAUSE_ANALYSIS, ProblemStatus.FIX_IN_PROGRESS),
    MARK_DUPLICATE(ProblemStatus.ASSESS, ProblemStatus.ROOT_CAUSE_ANALYSIS),
    CANCEL(ProblemStatus.ASSESS, ProblemStatus.ROOT_CAUSE_ANALYSIS),
    RE_ANALYZE(ProblemStatus.FIX_IN_PROGRESS, ProblemStatus.RESOLVED, ProblemStatus.CLOSED);

    private final Set<ProblemStatus> allowedIn;

    ProblemAction(ProblemStatus first, ProblemStatus... others) {
        this.allowedIn = EnumSet.of(first, others);
    }

    public boolean isAllowedIn(ProblemStatus status) {
        return allowedIn.contains(status);
    }

    /** The buttons to show for a problem in this status. */
    public static List<ProblemAction> availableIn(ProblemStatus status) {
        return Arrays.stream(values()).filter(action -> action.isAllowedIn(status)).toList();
    }
}
