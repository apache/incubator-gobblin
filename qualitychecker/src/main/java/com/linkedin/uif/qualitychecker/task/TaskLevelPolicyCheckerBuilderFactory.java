package com.linkedin.uif.qualitychecker.task;

import com.linkedin.uif.configuration.State;

public class TaskLevelPolicyCheckerBuilderFactory
{
    public TaskLevelPolicyCheckerBuilder newPolicyCheckerBuilder(State state) {
        return new TaskLevelPolicyCheckerBuilder(state);
    }
}
