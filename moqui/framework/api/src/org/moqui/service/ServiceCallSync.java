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
package org.moqui.service;

import java.util.Map;

public interface ServiceCallSync extends ServiceCall {
    /** If true suspend and create a new transaction if a transaction is active.
     * @return Reference to this for convenience.
     */
    ServiceCallSync requireNewTransaction(boolean requireNewTransaction);

    /** If null defaults to configured value for service, or container. For possible values see JavaDoc for javax.sql.Connection.
     * @return Reference to this for convenience.
     */
    ServiceCallSync transactionIsolation(int transactionIsolation);

    /** Call the service synchronously and immediately get the result.
     * @return Map containing the result (out parameters) from the service call.
     */
    Map<String, Object> call() throws ServiceException;
}
