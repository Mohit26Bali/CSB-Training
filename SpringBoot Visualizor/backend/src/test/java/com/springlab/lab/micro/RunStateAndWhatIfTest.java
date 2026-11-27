package com.springlab.lab.micro;

import com.springlab.exception.InvalidRequestException;
import com.springlab.lab.micro.run.RunState;
import com.springlab.lab.micro.run.WhatIfOption;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunStateAndWhatIfTest {

    @Test
    void onlyTheDocumentedTransitionsAreLegal() {
        assertThat(RunState.CREATED.allowedNext()).containsExactly(RunState.EXECUTING);
        assertThat(RunState.EXECUTING.allowedNext()).containsExactlyInAnyOrder(RunState.COMPLETED, RunState.FAILED);
        assertThat(RunState.COMPLETED.allowedNext()).containsExactly(RunState.CREATED);
        assertThat(RunState.FAILED.allowedNext()).containsExactly(RunState.CREATED);
        assertThat(RunState.CREATED.canMoveTo(RunState.COMPLETED)).as("a run cannot complete without executing").isFalse();
        assertThat(RunState.EXECUTING.canMoveTo(RunState.CREATED)).as("an executing run cannot be reset").isFalse();
        assertThat(RunState.COMPLETED.canMoveTo(RunState.EXECUTING)).as("a finished run cannot execute again without a reset").isFalse();
    }

    @Test
    void finishedStatesAreExactlyCompletedAndFailed() {
        assertThat(List.of(RunState.values()).stream().filter(RunState::isFinished)).containsExactlyInAnyOrder(RunState.COMPLETED, RunState.FAILED);
    }

    @Test
    void whatIfValuesAreValidatedAgainstTheirOptionNeverEchoed() {
        WhatIfOption number = WhatIfOption.integer("n", "N", 100, 50, 500);
        WhatIfOption flag = WhatIfOption.bool("b", "B", false);
        WhatIfOption choice = WhatIfOption.choice("c", "C", "a", List.of("a", "b"));
        assertThat(number.defaultValue()).isEqualTo("100");
        assertThatThrownBy(() -> number.validate("49")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> number.validate("501")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> number.validate("1 or 1=1")).isInstanceOf(InvalidRequestException.class)
                .hasMessageNotContaining("1=1");
        assertThatThrownBy(() -> flag.validate("yes")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> choice.validate("c")).isInstanceOf(InvalidRequestException.class);
        assertThat(number.validate("075")).isEqualTo("75");
    }
}
