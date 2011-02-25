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

import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobDataMap
import org.moqui.context.ExecutionContext
import org.moqui.Moqui

class ServiceQuartzJob implements Job {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ServiceQuartzJob.class)

    void execute(JobExecutionContext jobExecutionContext) {
        String serviceName = jobExecutionContext.jobDetail.key.group

        JobDataMap jdm = jobExecutionContext.jobDetail.jobDataMap
        Map parameters = new HashMap()
        for (String key in jdm.getKeys()) parameters.put(key, jdm.get(key))

        if (logger.infoEnabled) logger.info("Calling async|scheduled service [${serviceName}] with parameters [${parameters}]")

        ExecutionContext ec = Moqui.getExecutionContext()
        ec.service.sync().name(serviceName).parameters(parameters).call()
        ec.destroy()
    }
}
