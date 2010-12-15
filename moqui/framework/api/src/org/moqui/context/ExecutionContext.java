/*
 * Copyright 2010 David E. Jones
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.moqui.context;

import java.util.Map;

import org.moqui.entity.EntityFacade;
import org.moqui.service.ServiceFacade;

/**
 * Interface definition for object used throughout the Moqui Framework to manage contextual execution information and
 * tool interfaces. One instance of this object will exist for each thread running code and will be applicable for that
 * thread only.
 */
public interface ExecutionContext {
    /** Returns a Map that represents the current local variable space (context) in whatever is being run. */
    Map<String, Object> getContext();

    /** Returns a Map that represents the global/root variable space (context), ie the bottom of the context stack. */
    Map<String, Object> getContextRoot();

    /** Get current Tenant ID. A single application may be run in multiple virtual instances, one for each Tenant, and
     * each will have its own set of databases (except for the tenant database which is shared among all Tenants).
     */
    String getTenantId();

    /** For information about the user and user preferences (including locale, time zone, currency, etc). */
    UserFacade getUser();

    /** For user messages including general feedback, errors, and field-specific validation errors. */
    MessageFacade getMessage();

    /** For localization (l10n) functionality, like localizing messages. */
    L10nFacade getL10n();

    /** For accessing resources by location string (http://, jar://, component://, content://, classpath://, etc). */
    ResourceFacade getResource();

    /** For trace, error, etc logging to the console, files, etc. */
    LoggerFacade getLogger();

    /** For managing and accessing caches. */
    CacheFacade getCache();

    /** For transaction operations use this facade instead of the JTA UserTransaction and TransactionManager. See javadoc comments there for examples of code usage. */
    TransactionFacade getTransaction();

    /** For interactions with a relational database. */
    EntityFacade getEntity();

    /** For calling services (local or remote, sync or async or scheduled). */
    ServiceFacade getService();

    /** For rendering screens for general use (mostly for things other than web pages or web page snippets). */
    ScreenFacade getScreen();

    /** For information about artifacts as they are being executed. */
    ArtifactExecutionFacade getArtifactExecution();
}
