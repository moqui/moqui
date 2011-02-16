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
package org.moqui.impl.service.runner

import org.moqui.BaseException
import org.moqui.entity.EntityValue
import org.moqui.impl.service.ServiceDefinition
import org.moqui.impl.service.ServiceFacadeImpl
import org.moqui.impl.entity.EntityDefinition
import org.moqui.impl.service.ServiceRunner
import org.moqui.service.ServiceException

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.apache.commons.collections.set.ListOrderedSet

public class EntityAutoServiceRunner implements ServiceRunner {
    protected final static Logger logger = LoggerFactory.getLogger(EntityAutoServiceRunner.class)
    protected ServiceFacadeImpl sfi = null

    EntityAutoServiceRunner() {}

    public ServiceRunner init(ServiceFacadeImpl sfi) { this.sfi = sfi; return this }

    public Map<String, Object> runService(ServiceDefinition sd, Map<String, Object> parameters) {
        // check the verb and noun
        if (!sd.verb || ("create" != sd.verb && "update" != sd.verb && "delete" != sd.verb))
            throw new ServiceException("In service [${sd.serviceName}] the verb must be create, update, or delete for entity-auto type services.")
        if (!sd.noun)  throw new ServiceException("In service [${sd.serviceName}] you must specify a noun for entity-auto engine")

        EntityDefinition ed = sfi.ecfi.entityFacade.getEntityDefinition(sd.noun)
        if (!ed) throw new ServiceException("In service [${sd.serviceName}] the specified noun [${sd.noun}] is not a valid entity name")

        Map<String, Object> result = new HashMap()

        try {
            boolean allPksInOnly = true
            ListOrderedSet pkFieldNames = ed.getFieldNames(true, false)
            for (String pkFieldName in pkFieldNames) {
                if (!sd.getInParameter(pkFieldName) || sd.getOutParameter(pkFieldName)) { allPksInOnly = false; break }
            }

            if ("create" == sd.verb) {
                createEntity(sfi, ed, parameters, result, sd.getOutParameterNames())
            } else if ("update" == sd.verb) {
                /* <auto-attributes include="pk" mode="IN" optional="false"/> */
                if (!allPksInOnly) throw new ServiceException("In entity-auto type service [${sd.serviceName}] with update noun, not all pk fields have the mode IN")
                updateEntity(sfi, ed, parameters, result, sd.getOutParameterNames())
            } else if ("delete" == sd.verb) {
                /* <auto-attributes include="pk" mode="IN" optional="false"/> */
                if (!allPksInOnly) throw new ServiceException("In entity-auto type service [${sd.serviceName}] with delete noun, not all pk fields have the mode IN")
                deleteEntity(sfi, ed, parameters)
            }
        } catch (BaseException e) {
            throw new ServiceException("Error doing entity-auto operation for entity [${ed.entityName}] in service [${sd.serviceName}]", e)
        }

        return result
    }

    public static void createEntity(ServiceFacadeImpl sfi, EntityDefinition ed, Map<String, Object> parameters, Map<String, Object> result, Set<String> outParamNames) {
        EntityValue newEntityValue = sfi.ecfi.entityFacade.makeValue(ed.entityName)

        ListOrderedSet pkFieldNames = ed.getFieldNames(true, false)

        // always make fromDate optional, whether or not part of the pk; do this before the allPksIn check
        if (pkFieldNames.contains("fromDate") && !parameters.containsKey("fromDate")) {
            parameters.put("fromDate", sfi.ecfi.executionContext.user.nowTimestamp)
        }

        // see if all PK fields were passed in
        boolean allPksIn = true
        for (String pkFieldName in pkFieldNames) if (!parameters.get(pkFieldName)) { allPksIn = false; break }
        boolean isSinglePk = pkFieldNames.size() == 1
        boolean isDoublePk = pkFieldNames.size() == 2

        if (isSinglePk) {
            /* **** primary sequenced primary key **** */
            /* **** primary sequenced key with optional override passed in **** */
            String singlePkParamName = pkFieldNames.get(0)
            Node singlePkField = ed.getFieldNode(singlePkParamName)

            Object pkValue = parameters.get(singlePkField."@name")
            if (!pkValue) pkValue = sfi.ecfi.entityFacade.sequencedIdPrimary(ed.entityName, null)
            newEntityValue.set(singlePkField."@name", pkValue)
            if (outParamNames == null || outParamNames.contains(singlePkParamName)) result.put(singlePkParamName, pkValue)
        } else if (isDoublePk && !allPksIn) {
            /* **** secondary sequenced primary key **** */
            String doublePkSecondaryName = parameters.get(pkFieldNames.get(0)) ? pkFieldNames.get(1) : pkFieldNames.get(0)
            newEntityValue.setFields(parameters, true, null, true)
            sfi.ecfi.entityFacade.sequencedIdSecondary(newEntityValue, doublePkSecondaryName, 5, 1)
            if (outParamNames == null || outParamNames.contains(doublePkSecondaryName))
                result.put(doublePkSecondaryName, newEntityValue.get(doublePkSecondaryName))
        } else if (allPksIn) {
            /* **** plain specified primary key **** */
            newEntityValue.setFields(parameters, true, null, true)
        } else {
            throw new ServiceException("In entity-auto create service for entity [${ed.entityName}]: " +
                    "could not find a valid combination of primary key settings to do a create operation; options include: " +
                    "1. a single entity primary-key field for primary auto-sequencing with or without matching in-parameter, and with or without matching out-parameter for the possibly sequenced value, " +
                    "2. a 2-part entity primary-key with one part passed in as an in-parameter (existing primary pk value) and with or without the other part defined as an out-parameter (the secodnary pk to sub-sequence), " +
                    "3. all entity pk fields are passed into the service");
        }

        newEntityValue.setFields(parameters, true, null, false)
        // logger.info("In auto createEntity allPksIn [${allPksIn}] isSinglePk [${isSinglePk}] isDoublePk [${isDoublePk}] newEntityValue final [${newEntityValue}]")
        newEntityValue.create()
    }

    public static void updateEntity(ServiceFacadeImpl sfi, EntityDefinition ed, Map<String, Object> parameters, Map<String, Object> result, Set<String> outParamNames) {
        EntityValue lookedUpValue = sfi.ecfi.entityFacade.makeFind(ed.entityName).condition(parameters).useCache(false).forUpdate(true).one()
        if (lookedUpValue == null) {
            throw new ServiceException("In entity-auto update service for entity [${ed.entityName}] value not found, cannot update; using parameters [${parameters}]")
        }

        // populate the oldStatusId out if there is a service parameter for it, and before we do the set non-pk fields
        Node statusIdField = ed.getFieldNode("statusId")
        if ((outParamNames == null || outParamNames.contains("oldStatusId")) && statusIdField) {
            result.put("oldStatusId", lookedUpValue.get("statusId"))
        }

        // do the StatusValidChange check
        String parameterStatusId = (String) parameters.get("statusId")
        if (parameterStatusId && statusIdField) {
            String lookedUpStatusId = (String) lookedUpValue.get("statusId")
            if (lookedUpStatusId && !parameterStatusId.equals(lookedUpStatusId)) {
                // there was an old status, and in this call we are trying to change it, so do the StatusValidChange check
                EntityValue statusValidChange = sfi.ecfi.entityFacade.makeFind("StatusValidChange")
                        .condition(["statusId":lookedUpStatusId, "statusIdTo":parameterStatusId])
                        .useCache(true).one()
                if (!statusValidChange) {
                    // uh-oh, no valid change...
                    throw new ServiceException("In entity-auto update service for entity [${ed.entityName}] no status change was found going from status [${lookedUpStatusId}] to status [${parameterStatusId}]")
                }
            }
        }

        // NOTE: nothing here to maintain the status history, that should be done with a custom service called by SECA rule

        lookedUpValue.setFields(parameters, true, null, false)
        // logger.info("In auto updateEntity lookedUpValue final [${lookedUpValue}] for parameters [${parameters}]")
        lookedUpValue.update()
    }

    public static void deleteEntity(ServiceFacadeImpl sfi, EntityDefinition ed, Map<String, Object> parameters) {
        EntityValue lookedUpValue = sfi.ecfi.entityFacade.makeFind(ed.entityName).condition(parameters).useCache(false).one()
        if (lookedUpValue != null) lookedUpValue.delete()
    }

    public void destroy() { }
}
