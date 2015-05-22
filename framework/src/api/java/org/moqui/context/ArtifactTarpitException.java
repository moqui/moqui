/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.context;

import org.moqui.BaseException;

/** Thrown when artifact tarpit is hit, too many uses of artifact. */
public class ArtifactTarpitException extends BaseException {

    Integer retryAfterSeconds = null;

    public ArtifactTarpitException(String str) { super(str); }
    public ArtifactTarpitException(String str, Integer retryAfterSeconds) {
        super(str);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ArtifactTarpitException(String str, Throwable nested) {
        super(str, nested);
    }

    public Integer getRetryAfterSeconds() { return retryAfterSeconds; }
}
