/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.impl.context;

import groovy.lang.Binding;

import java.util.Map;

public class ContextBinding extends Binding {
    public ContextBinding(Map variables) { super(variables); }

    @Override
    public Object getVariable(String name) {
        // NOTE: this code is part of the original Groovy groovy.lang.Binding.getVariable() method and leaving it out
        //     is the reason to override this method:
        //if (result == null && !variables.containsKey(name)) {
        //    throw new MissingPropertyException(name, this.getClass());
        //}
        return getVariables().get(name);
    }

    @Override
    public boolean hasVariable(String name) {
        // always treat it like the variable exists and is null to change the behavior for variable scope and
        //     declaration, easier in simple scripts
        return true;
    }
}
