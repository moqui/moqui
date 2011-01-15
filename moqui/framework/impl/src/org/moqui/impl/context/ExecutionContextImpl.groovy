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
package org.moqui.impl.context

import org.moqui.context.ExecutionContext
import org.moqui.context.UserFacade
import org.moqui.context.MessageFacade
import org.moqui.context.L10nFacade
import org.moqui.context.ResourceFacade
import org.moqui.context.LoggerFacade
import org.moqui.context.CacheFacade
import org.moqui.context.TransactionFacade
import org.moqui.entity.EntityFacade
import org.moqui.service.ServiceFacade
import org.moqui.context.ScreenFacade
import org.moqui.context.ArtifactExecutionFacade

class ExecutionContextImpl implements ExecutionContext {

    ExecutionContextFactoryImpl ecfi

    protected ContextStack context = new ContextStack()
    protected String tenantId = null

    protected UserFacadeImpl userFacade
    protected MessageFacadeImpl messageFacade
    protected L10nFacadeImpl l10nFacade
    protected ArtifactExecutionFacadeImpl artifactExecutionFacade

    ExecutionContextImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi
        this.userFacade = new UserFacadeImpl(this)
        this.messageFacade = new MessageFacadeImpl()
        this.l10nFacade = new L10nFacadeImpl(this)
        this.artifactExecutionFacade = new ArtifactExecutionFacadeImpl(this)

        context.put("ec", this)
    }

    /** @see org.moqui.context.ExecutionContext#getContext() */
    Map<String, Object> getContext() { return this.context }

    /** @see org.moqui.context.ExecutionContext#getContextRoot() */
    Map<String, Object> getContextRoot() { return this.context.getRootMap() }

    boolean compareInContext(String fieldName, String operator, String value, String toFieldName, String format, String type) {
        def field = context.get(fieldName)
        def toField = toFieldName ? context.get(toFieldName) : value

        if (format) {
            // TODO handle type conversion with format for Date, Time, Timestamp
        }

        switch (type) {
            case "String": field = field as String; toField = toField as String; break;
            case "BigDecimal": field = field as BigDecimal; toField = toField as BigDecimal; break;
            case "Double": field = field as Double; toField = toField as Double; break;
            case "Float": field = field as Double; toField = toField as Double; break;
            case "Long": field = field as Long; toField = toField as Long; break;
            case "Integer": field = field as Long; toField = toField as Long; break;
            case "Date": field = field as java.sql.Date; toField = toField as java.sql.Date; break;
            case "Time": field = field as java.sql.Time; toField = toField as java.sql.Time; break;
            case "Timestamp": field = field as java.sql.Timestamp; toField = toField as java.sql.Timestamp; break;
            case "Boolean": field = field as Boolean; toField = toField as Boolean; break;
            case "Object":
            default: break; // do nothing for Object or by default
        }

        boolean result
        switch (operator) {
            case "less": result = (field < toField); break;
            case "greater": result = (field > toField); break;
            case "less-equals": result = (field <= toField); break;
            case "greater-equals": result = (field >= toField); break;

            case "contains": result = (field as String).contains(toField as String); break;
            case "not-contains": result = !(field as String).contains(toField as String); break;

            case "empty": result = (field ? true : false); break;
            case "not-empty": result = (field ? false : true); break;

            case "matches": result = (field as String).matches(toField as String); break;
            case "not-matches": result = !(field as String).matches(toField as String); break;

            case "not-equals": result = (field != toField); break;
            case "equals":
            default: result = (field == toField)
            break;
        }
        return result
    }

    /** @see org.moqui.context.ExecutionContext#getTenantId() */
    String getTenantId() { return this.tenantId }

    /** @see org.moqui.context.ExecutionContext#getUser() */
    UserFacade getUser() { return this.userFacade }

    /** @see org.moqui.context.ExecutionContext#getMessage() */
    MessageFacade getMessage() { return this.messageFacade }

    /** @see org.moqui.context.ExecutionContext#getL10n() */
    L10nFacade getL10n() { return this.l10nFacade }

    /** @see org.moqui.context.ExecutionContext#getArtifactExecution() */
    ArtifactExecutionFacade getArtifactExecution() { return this.artifactExecutionFacade }

    // ==== More Permanent Objects (get from the factory instead of locally) ===

    /** @see org.moqui.context.ExecutionContext#getResource() */
    ResourceFacade getResource() { return this.ecfi.getResourceFacade() }

    /** @see org.moqui.context.ExecutionContext#getLogger() */
    LoggerFacade getLogger() { return this.ecfi.getLoggerFacade() }

    /** @see org.moqui.context.ExecutionContext#getCache() */
    CacheFacade getCache() { return this.ecfi.getCacheFacade() }

    /** @see org.moqui.context.ExecutionContext#getTransaction() */
    TransactionFacade getTransaction() { return this.ecfi.getTransactionFacade() }

    /** @see org.moqui.context.ExecutionContext#getEntity() */
    EntityFacade getEntity() { return this.ecfi.getEntityFacade() }

    /** @see org.moqui.context.ExecutionContext#getService() */
    ServiceFacade getService() { return this.ecfi.getServiceFacade() }

    /** @see org.moqui.context.ExecutionContext#getScreen() */
    ScreenFacade getScreen() { return this.ecfi.getScreenFacade() }

    /** @see org.moqui.context.ExecutionContext#destroy() */
    void destroy() {
        // TODO?: make sure there are no db connections open, if so close them
        // make sure there are no transactions open, if any commit them all now
        this.ecfi.transactionFacade.destroyAllInThread()
    }
}
