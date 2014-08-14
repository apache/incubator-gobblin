package com.linkedin.uif.test;

import java.util.Collection;

import com.linkedin.uif.configuration.State;
import com.linkedin.uif.configuration.WorkUnitState;
import com.linkedin.uif.publisher.DataPublisher;

/**
 * An implementation of {@link DataPublisher} for integration test.
 *
 * <p>
 *     This is a dummy implementation that exists purely to make
 *     integration test work.
 * </p>
 */
public class TestDataPublisher extends DataPublisher {

    public TestDataPublisher(State state) {
        super(state);
    }

    @Override
    public void initialize() throws Exception {
        // Do nothing
    }

    @Override
    public void close() throws Exception {
        // Do nothing
    }

    @Override
    public void publishData(Collection<? extends WorkUnitState> tasks) throws Exception {
    }

    @Override
    public void publishMetadata(Collection<? extends WorkUnitState> tasks) throws Exception {
    }
}