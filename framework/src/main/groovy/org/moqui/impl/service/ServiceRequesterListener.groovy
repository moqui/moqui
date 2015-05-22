/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.impl.service

import org.quartz.JobListener
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException

import org.moqui.service.ServiceResultReceiver
import org.quartz.TriggerListener
import org.quartz.Trigger
import org.quartz.Trigger.CompletedExecutionInstruction

class ServiceRequesterListener implements JobListener, TriggerListener {
    protected ServiceResultReceiver resultReceiver

    ServiceRequesterListener(ServiceResultReceiver resultReceiver) {
        this.resultReceiver = resultReceiver
    }

    String getName() {
        // TODO: does this have to be unique?
        return "MoquiServiceRequesterListener"
    }

    void jobToBeExecuted(JobExecutionContext jobExecutionContext) {
    }

    void jobExecutionVetoed(JobExecutionContext jobExecutionContext) {
    }

    void jobWasExecuted(JobExecutionContext jobExecutionContext, JobExecutionException jobExecutionException) {
        if (jobExecutionException) {
            resultReceiver.receiveThrowable(jobExecutionException.getUnderlyingException())
        } else {
            // TODO: need to clean up Map based on out-parameter defs?
            resultReceiver.receiveResult(jobExecutionContext.getMergedJobDataMap())
        }
    }

    void triggerFired(Trigger trigger, JobExecutionContext jobExecutionContext) { }

    boolean vetoJobExecution(Trigger trigger, JobExecutionContext jobExecutionContext) {
        // for now, never veto
        return false
    }

    void triggerMisfired(Trigger trigger) { }

    void triggerComplete(Trigger trigger, JobExecutionContext jobExecutionContext,
                         CompletedExecutionInstruction completedExecutionInstruction) {
    }
}
