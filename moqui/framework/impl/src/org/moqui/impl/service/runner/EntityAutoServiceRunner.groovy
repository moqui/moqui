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

import org.moqui.impl.service.ServiceDefinition
import org.moqui.impl.service.ServiceFacadeImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.moqui.service.ServiceException
import org.moqui.impl.entity.EntityDefinition
import org.moqui.BaseException
import org.moqui.entity.EntityValue

public class EntityAutoServiceRunner implements ServiceRunner {
    protected final static Logger logger = LoggerFactory.getLogger(EntityAutoServiceRunner.class)
    protected ServiceFacadeImpl sfi = null

    EntityAutoServiceRunner() {}

    public ServiceRunner init(ServiceFacadeImpl sfi) { this.sfi = sfi; return this }

    public Map<String, Object> runService(ServiceDefinition sd, Map<String, Object> context) {
        // check the verb and noun
        if (!sd.verb || ("create" != sd.verb && "update" != sd.verb && "delete" != sd.verb))
            throw new ServiceException("In service [${sd.serviceName}] the verb must be create, update, or delete for entity-auto type services.")
        if (!sd.noun)  throw new ServiceException("In service [${sd.serviceName}] you must specify a noun for entity-auto engine")

        EntityDefinition ed = sfi.ecfi.entityFacade.getEntityDefinition(sd.noun)
        if (!ed) throw new ServiceException("In service [${sd.serviceName}] the specified noun [${sd.noun}] is not a valid entity name")

        Map<String, Object> result = new HashMap()

        try {
            boolean allPksInOnly = true
            List<String> pkFieldNames = ed.getFieldNames(true, false)
            for (String pkFieldName in pkFieldNames) {
                if (!sd.getInParameter(pkFieldName) || sd.getOutParameter(pkFieldName)) { allPksInOnly = false; break }
            }

            if ("create" == sd.verb) {
                EntityValue newEntity = sfi.ecfi.entityFacade.makeValue(ed.entityName)

                boolean isSinglePk = pkFieldNames.size() == 1
                boolean isDoublePk = pkFieldNames.size() == 2

                Node singlePkField = isSinglePk ? ed.getFieldNode(pkFieldNames[0]) : null
                Node singlePkParamIn = isSinglePk ? sd.getInParameter(pkFieldNames[0]) : null
                Node singlePkParamOut = isSinglePk ? sd.getOutParameter(pkFieldNames[0]) : null

                Node doublePkPrimaryInParam = null
                Node doublePkSecondaryOutParam = null
                Node doublePkSecondaryOutField = null
                if (isDoublePk) {
                    if (sd.getInParameter(pkFieldNames[0]) && sd.getOutParameter(pkFieldNames[1])) {
                        doublePkPrimaryInParam = sd.getInParameter(pkFieldNames[0])
                        doublePkSecondaryOutParam = sd.getOutParameter(pkFieldNames[1])
                        doublePkSecondaryOutField = ed.getFieldNode(pkFieldNames[1])
                    } else if (sd.getOutParameter(pkFieldNames[0]) && sd.getInParameter(pkFieldNames[1])) {
                        doublePkPrimaryInParam = sd.getInParameter(pkFieldNames[1])
                        doublePkSecondaryOutParam = sd.getOutParameter(pkFieldNames[0])
                        doublePkSecondaryOutField = ed.getFieldNode(pkFieldNames[0])
                    }
                    // otherwise we don't have an IN and an OUT... so do nothing and leave them null
                }


                if (isSinglePk && singlePkParamOut && !singlePkParamIn) {
                    /* **** primary sequenced primary key **** */
                    String sequencedId = sfi.ecfi.entityFacade.sequencedIdPrimary(ed.entityName, null)
                    newEntity.set(singlePkField."@name", sequencedId)
                    result.put(singlePkParamOut."@name", sequencedId)
                } else if (isSinglePk && singlePkParamOut && singlePkParamIn) {
                    /* **** primary sequenced key with optional override passed in **** */
                    Object pkValue = context.get(singlePkField."@name")
                    if (!pkValue) pkValue = sfi.ecfi.entityFacade.sequencedIdPrimary(ed.entityName, null)
                    newEntity.set(singlePkField."@name", pkValue)
                    result.put(singlePkParamOut."@name", pkValue)
                } else if (isDoublePk && doublePkPrimaryInParam != null && doublePkSecondaryOutParam != null) {
                    /* **** secondary sequenced primary key **** */
                    newEntity.setFields(context, true, null, true)
                    sfi.ecfi.entityFacade.sequencedIdSecondary(newEntity, doublePkSecondaryOutField."@name", 5, 1)
                    result.put(doublePkSecondaryOutParam."@name", newEntity.get(doublePkSecondaryOutField."@name"))
                } else if (allPksInOnly) {
                    /* **** plain specified primary key **** */
                    newEntity.setFields(context, true, null, true)
                } else {
                    throw new ServiceException("In Service [${sd.serviceName}] which uses the entity-auto engine with the create invoke option: " +
                            "could not find a valid combination of primary key settings to do a known create operation; options include: " +
                            "1. a single OUT pk for primary auto-sequencing, " +
                            "2. a single IN and OUT pk for primary auto-sequencing with optional override, " +
                            "3. a 2-part pk with one part IN (existing primary pk) and one part OUT (the secdonary pk to sub-sequence, " +
                            "4. all pk fields are IN for a manually specified primary key");
                }

                newEntity.setFields(context, true, null, false)

                // handle the case where there is a fromDate in the pk of the entity, and it is optional or undefined in the service def, populate automatically
                Node fromDateField = ed.getFieldNode("fromDate")
                if (fromDateField != null && fromDateField."@is-pk" == "true") {
                    Node fromDateParamIn = sd.getInParameter("fromDate")
                    if (!fromDateParamIn || (fromDateParamIn."@required" != "true" && !context.get("fromDate"))) {
                        newEntity.set("fromDate", sfi.ecfi.executionContext.user.nowTimestamp)
                    }
                }

                newEntity.create()
            } else if ("update" == sd.verb) {
                /* <auto-attributes include="pk" mode="IN" optional="false"/> */
                if (!allPksInOnly) throw new ServiceException("In entity-auto type service [${sd.serviceName}] with update noun, not all pk fields have the mode IN")

                EntityValue lookedUpValue = sfi.ecfi.entityFacade.makeFind(ed.entityName).condition(context).useCache(false).one()
                if (lookedUpValue == null) {
                    throw new ServiceException("In entity-auto update service [${sd.serviceName}] value not found, cannot update")
                }

                // populate the oldStatusId out if there is a service parameter for it, and before we do the set non-pk fields
                Node statusIdParamIn = sd.getInParameter("statusId")
                Node statusIdField = ed.getFieldNode("statusId")
                Node oldStatusIdParamOut = sd.getOutParameter("oldStatusId")
                if (statusIdParamIn && oldStatusIdParamOut && statusIdField) {
                    result.put("oldStatusId", lookedUpValue.get("statusId"))
                }

                // do the StatusValidChange check
                String parameterStatusId = (String) context.get("statusId")
                if (statusIdParamIn && parameterStatusId && statusIdField) {
                    String lookedUpStatusId = (String) lookedUpValue.get("statusId")
                    if (lookedUpStatusId && !parameterStatusId.equals(lookedUpStatusId)) {
                        // there was an old status, and in this call we are trying to change it, so do the StatusValidChange check

                        EntityValue statusValidChange = sfi.ecfi.entityFacade.makeFind("StatusValidChange")
                                .condition(["statusId":lookedUpStatusId, "statusIdTo":parameterStatusId])
                                .useCache(true).one()
                        if (!statusValidChange) {
                            // uh-oh, no valid change...
                            throw new ServiceException("In service [${sd.serviceName}] no status change was found going from status [${lookedUpStatusId}] to status [${parameterStatusId}]")
                        }
                    }
                }

                // NOTE: nothing here to maintain the status history, that should be done with a custom service called by SECA rule

                lookedUpValue.setFields(context, true, null, false)
                lookedUpValue.update()
            } else if ("delete" == sd.verb) {
                /* <auto-attributes include="pk" mode="IN" optional="false"/> */
                if (!allPksInOnly) throw new ServiceException("In entity-auto type service [${sd.serviceName}] with delete noun, not all pk fields have the mode IN")

                EntityValue lookedUpValue = sfi.ecfi.entityFacade.makeFind(ed.entityName).condition(context).useCache(false).one()
                if (lookedUpValue != null) lookedUpValue.delete()
            }
        } catch (BaseException e) {
            throw new ServiceException("Error doing entity-auto operation for entity [${ed.entityName}] in service [${sd.serviceName}]", e)
        }

        return result
    }

    public void destroy() { }
}
