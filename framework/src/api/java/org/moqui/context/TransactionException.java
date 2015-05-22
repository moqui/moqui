/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.context;

import org.moqui.BaseException;

/**
 * TransactionException
 */
public class TransactionException extends BaseException {

    public TransactionException(String str) {
        super(str);
    }

    public TransactionException(String str, Throwable nested) {
        super(str, nested);
    }
}
