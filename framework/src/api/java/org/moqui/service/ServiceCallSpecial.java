/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.service;

import java.util.Map;

public interface ServiceCallSpecial extends ServiceCall {
    /** Name of the service to run. The combined service name, like: "${path}.${verb}${noun}". To explicitly separate
     * the verb and noun put a hash (#) between them, like: "${path}.${verb}#${noun}" (this is useful for calling the
     * implicit entity CrUD services where verb is create, update, or delete and noun is the name of the entity).
     */
    ServiceCallSpecial name(String serviceName);

    ServiceCallSpecial name(String verb, String noun);

    ServiceCallSpecial name(String path, String verb, String noun);

    /** Map of name, value pairs that make up the context (in parameters) passed to the service. */
    ServiceCallSpecial parameters(Map<String, ?> context);

    /** Single name, value pairs to put in the context (in parameters) passed to the service. */
    ServiceCallSpecial parameter(String name, Object value);


    /** Add a service to run on commit of the current transaction using the ServiceXaWrapper */
    void registerOnCommit();

    /** Add a service to run on rollback of the current transaction using the ServiceXaWrapper */
    void registerOnRollback();
}
