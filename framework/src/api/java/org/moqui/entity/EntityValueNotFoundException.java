/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.entity;

public class EntityValueNotFoundException extends EntityException {

    public EntityValueNotFoundException(String str) {
        super(str);
    }

    public EntityValueNotFoundException(String str, Throwable nested) {
        super(str, nested);
    }
}
