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
package org.moqui;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * BaseException - the base/root exception for all exception classes in Moqui Framework.
 */
public class BaseException extends RuntimeException {
    public BaseException(String message) {
        super(message);
    }

    public BaseException(String message, Throwable nested) {
        super(message, nested);
    }

    @Override
    public void printStackTrace() {
        this.setStackTrace(getFilteredStackTrace());
        super.printStackTrace();
    }

    @Override
    public void printStackTrace(PrintStream printStream) {
        this.setStackTrace(getFilteredStackTrace());
        super.printStackTrace(printStream);
    }

    @Override
    public void printStackTrace(PrintWriter printWriter) {
        this.setStackTrace(getFilteredStackTrace());
        super.printStackTrace(printWriter);
    }

    @Override
    public StackTraceElement[] getStackTrace() {
        return getFilteredStackTrace();
    }

    public StackTraceElement[] getFilteredStackTrace() {
        StackTraceElement[] orig = super.getStackTrace();
        List<StackTraceElement> newList = new ArrayList<StackTraceElement>(orig.length);
        for (StackTraceElement ste: orig) {
            String cn = ste.getClassName();
            if (cn.startsWith("freemarker.core.") || cn.startsWith("freemarker.ext.beans.") ||
                    cn.startsWith("java.lang.reflect.") || cn.startsWith("sun.reflect.") ||
                    cn.startsWith("org.codehaus.groovy.runtime.") || cn.startsWith("org.codehaus.groovy.reflection.") ||
                    cn.startsWith("groovy.lang.")) {
                continue;
            }
            if ("renderSingle".equals(ste.getMethodName()) && cn.startsWith("org.moqui.impl.screen.ScreenSection")) continue;
            if (("internalRender".equals(ste.getMethodName()) || "doActualRender".equals(ste.getMethodName())) && cn.startsWith("org.moqui.impl.screen.ScreenRenderImpl")) continue;
            if (("call".equals(ste.getMethodName()) || "callCurrent".equals(ste.getMethodName())) && ste.getLineNumber() == -1) continue;
            //System.out.println("Adding className: " + cn + ", line: " + ste.getLineNumber());
            newList.add(ste);
        }
        //System.out.println("Called getFilteredStackTrace, orig.length=" + orig.length + ", newList.size()=" + newList.size());
        return newList.toArray(new StackTraceElement[newList.size()]);
    }
}
