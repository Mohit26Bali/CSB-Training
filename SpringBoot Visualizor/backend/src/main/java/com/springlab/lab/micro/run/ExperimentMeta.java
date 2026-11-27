package com.springlab.lab.micro.run;

import java.util.List;

/** What the catalogue says about an experiment: shown to learners and used to validate a run request. */
public record ExperimentMeta(String id, String topic, String title, String question, String claim, String diagram, List<WhatIfOption> whatIfs) {
}
