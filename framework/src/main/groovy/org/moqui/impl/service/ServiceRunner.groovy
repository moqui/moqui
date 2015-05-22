/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.impl.service

import org.moqui.service.ServiceException

interface ServiceRunner {
    ServiceRunner init(ServiceFacadeImpl sfi);
    Map<String, Object> runService(ServiceDefinition sd, Map<String, Object> parameters) throws ServiceException;
    void destroy();
}
