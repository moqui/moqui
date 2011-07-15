/*
 * This Work is in the public domain and is provided on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE,
 * NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 * You are solely responsible for determining the appropriateness of using
 * this Work and assume any risks associated with your use of this Work.
 *
 * This Work includes contributions authored by David E. Jones, not as a
 * "work for hire", who hereby disclaims any copyright to the same.
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
