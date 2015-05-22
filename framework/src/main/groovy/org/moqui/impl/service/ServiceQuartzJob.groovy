/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.impl.service

import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobDataMap
import org.moqui.context.ExecutionContext
import org.moqui.Moqui

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ServiceQuartzJob implements Job {
    protected final static Logger logger = LoggerFactory.getLogger(ServiceQuartzJob.class)

    void execute(JobExecutionContext jobExecutionContext) {
        String serviceName = jobExecutionContext.jobDetail.key.group

        JobDataMap jdm = jobExecutionContext.getJobDetail().getJobDataMap()
        JobDataMap tjdm = jobExecutionContext.getTrigger().getJobDataMap()
        Map parameters = new HashMap()
        for (String key in jdm.getKeys()) parameters.put(key, jdm.get(key))
        for (String key in tjdm.getKeys()) parameters.put(key, tjdm.get(key))

        if (logger.traceEnabled) logger.trace("Calling async|scheduled service [${serviceName}] with parameters [${parameters}]")

        ExecutionContext ec = Moqui.getExecutionContext()

        try {
            String userId = parameters.authUserAccount?.userId ?: parameters.authUsername
            String password = parameters.authUserAccount?.currentPassword ?: parameters.authPassword
            String tenantId = parameters.authTenantId

            // logger.warn("=========== running quartz job for ${serviceName}, userId=${userId}, parameters: ${parameters}")

            boolean needsAuthzEnable = false
            if (userId && password) {
                ec.getUser().loginUser(userId, password, tenantId)
            } else if (userId) {
                // debatable if this is the best idea, introduces a security hole with control over job scheduling,
                //     but that is true in general for execution of server-side code, there are various ways to get
                //     around authc that are just less convenient
                ec.getUser().internalLoginUser(userId, tenantId)
                // authz check will be done when job is scheduled for this sort of case, so don't check authz here
                needsAuthzEnable = !ec.getArtifactExecution().disableAuthz()
                // logger.warn("=========== internalLoginUser in job for ${serviceName}, userId=${userId}, ec.user.username: ${ec.user.username}")
            } else if (tenantId) {
                ec.changeTenant(tenantId)
            }

            // logger.warn("=========== running quartz job for ${serviceName}, parameter tenantId=${tenantId}, active tenantId=${ec.getTenantId()}, parameters: ${parameters}")

            try {
                ec.service.sync().name(serviceName).parameters(parameters).call()
            } finally {
                if (needsAuthzEnable) ec.getArtifactExecution().enableAuthz()
            }
        } catch (Throwable t) {
            logger.error("Error calling service [${serviceName}] with parameters [${parameters}]", t)
            ec.message.addError(t.message)
            Throwable parent = t.cause
            while (parent != null) {
                ec.message.addError(parent.message)
                parent = parent.cause
            }
        }

        if (ec.getMessage().hasError()) {
            StringBuilder sb = new StringBuilder()
            sb.append("Error calling service [${serviceName}] with parameters [${parameters}]\n")
            sb.append(ec.getMessage().getErrorsString())
            logger.error(sb.toString())

            // TODO handle retry on error with max-retry?
        }

        ec.destroy()
    }
}
