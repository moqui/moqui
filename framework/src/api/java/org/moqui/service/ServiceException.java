/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.service;

/**
 * ServiceFacade Exception
 */
public class ServiceException extends org.moqui.BaseException {

    public ServiceException(String str) {
        super(str);
    }

    public ServiceException(String str, Throwable nested) {
        super(str, nested);
    }
}
