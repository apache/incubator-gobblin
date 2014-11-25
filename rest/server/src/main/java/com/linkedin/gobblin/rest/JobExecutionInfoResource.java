package com.linkedin.gobblin.rest;

import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

import com.linkedin.restli.common.ComplexResourceKey;
import com.linkedin.restli.common.EmptyRecord;
import com.linkedin.restli.server.annotations.RestLiCollection;
import com.linkedin.restli.server.resources.ComplexKeyResourceTemplate;

import com.linkedin.uif.metastore.JobHistoryStore;

/**
 * A Rest.li resource for serving queries of Gobblin job executions.
 *
 * @author ynli
 */
@RestLiCollection(name = "jobExecutions", namespace = "com.linkedin.gobblin.rest")
public class JobExecutionInfoResource extends
        ComplexKeyResourceTemplate<JobExecutionQuery, EmptyRecord, JobExecutionQueryResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobExecutionInfoResource.class);

    @Inject @Named("jobHistoryStore")
    private JobHistoryStore jobHistoryStore;

    @Override
    public JobExecutionQueryResult get(ComplexResourceKey<JobExecutionQuery, EmptyRecord> key) {
        JobExecutionQuery query = key.getKey();

        JobExecutionInfoArray jobExecutionInfos = new JobExecutionInfoArray();
        try {
            for (JobExecutionInfo jobExecutionInfo : this.jobHistoryStore.get(query)) {
                jobExecutionInfos.add(jobExecutionInfo);
            }
        } catch (Throwable t) {
            LOGGER.error(String.format("Failed to execute query [id = %s, type = %s]",
                    query.getId(), query.getIdType().name()), t);
            return null;
        }

        JobExecutionQueryResult result = new JobExecutionQueryResult();
        result.setJobExecutions(jobExecutionInfos);
        return result;
    }

    @Override
    public Map<ComplexResourceKey<JobExecutionQuery, EmptyRecord>, JobExecutionQueryResult> batchGet(
            Set<ComplexResourceKey<JobExecutionQuery, EmptyRecord>> keys) {

        Map<ComplexResourceKey<JobExecutionQuery, EmptyRecord>, JobExecutionQueryResult> results =
                Maps.newHashMap();
        for (ComplexResourceKey<JobExecutionQuery, EmptyRecord> key : keys) {
            JobExecutionQueryResult result = get(key);
            if (result != null) {
                results.put(key, get(key));
            }
        }

        return results;
    }
}
