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
package org.moqui.impl.context;

import groovy.lang.Binding;

import java.util.Map;

public class ContextBinding extends Binding {
    public ContextBinding(Map variables) { super(variables); }

    @Override
    public Object getVariable(String name) {
        Object result = getVariables().get(name);

        // NOTE: this code is part of the original Groovy groovy.lang.Binding.getVariable() method and leaving it out
        //     is the reason to override this method:
        //if (result == null && !variables.containsKey(name)) {
        //    throw new MissingPropertyException(name, this.getClass());
        //}

        return result;
    }

    @Override
    public boolean hasVariable(String name) {
        // always treat it like the variable exists and is null to change the behavior for variable scope and
        //     declaration, easier in simple scripts
        return true;
    }
}
