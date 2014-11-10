package com.linkedin.uif.runtime;

import org.apache.commons.mail.EmailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linkedin.uif.configuration.ConfigurationKeys;
import com.linkedin.uif.util.EmailUtils;

/**
 * An implementation of {@link JobListener} that sends a notification .
 */
public class EmailNotificationJobListener implements JobListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailNotificationJobListener.class);

    @Override
    public void jobCompleted(JobState jobState) {
        boolean alertEmailEnabled = Boolean.valueOf(jobState.getProp(
                ConfigurationKeys.ALERT_EMAIL_ENABLED_KEY, Boolean.toString(false)));
        boolean notificationEmailEnabled = Boolean.valueOf(jobState.getProp(
                ConfigurationKeys.NOTIFICATION_EMAIL_ENABLED_KEY, Boolean.toString(false)));

        // Send out alert email if the maximum number of consecutive failures is reached
        if (jobState.getState() == JobState.RunningState.FAILED) {
            int failures = jobState.getPropAsInt(ConfigurationKeys.JOB_FAILURES_KEY, 0);
            int maxFailures = jobState.getPropAsInt(ConfigurationKeys.JOB_MAX_FAILURES_KEY,
                    ConfigurationKeys.DEFAULT_JOB_MAX_FAILURES);
            if (alertEmailEnabled && failures >= maxFailures) {
                try {
                    EmailUtils.sendJobFailureAlertEmail(
                            jobState.getJobName(), jobState.toString(), failures, jobState);
                } catch (EmailException ee) {
                    LOGGER.error("Failed to send job failure alert email for job " + jobState.getJobId(), ee);
                }
                return;
            }
        }

        if (notificationEmailEnabled) {
            try {
                EmailUtils.sendJobCompletionEmail(jobState.getJobName(), jobState.toString(),
                        jobState.getState().name(), jobState);
            } catch (EmailException ee) {
                LOGGER.error("Failed to send job completion notification email for job " + jobState.getJobId(), ee);
            }
        }
    }
}
