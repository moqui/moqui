/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.context;

import org.moqui.BaseException;

/** Thrown when artifact authz fails. */
public class ArtifactAuthorizationException extends BaseException {

    public ArtifactAuthorizationException(String str) {
        super(str);
    }

    public ArtifactAuthorizationException(String str, Throwable nested) {
        super(str, nested);
    }
}
