/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.entity;

/**
 * EntityException
 *
 */
public class EntityException extends org.moqui.BaseException {

    public EntityException(String str) {
        super(str);
    }

    public EntityException(String str, Throwable nested) {
        super(str, nested);
    }
}
