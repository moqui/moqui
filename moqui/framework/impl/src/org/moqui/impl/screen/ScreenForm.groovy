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
package org.moqui.impl.screen

import org.moqui.impl.actions.XmlAction
import org.moqui.impl.context.ContextStack
import org.moqui.impl.context.ExecutionContextFactoryImpl

class ScreenForm {
    protected Node formNode
    protected String location

    protected XmlAction rowActions = null

    ScreenForm(ExecutionContextFactoryImpl ecfi, Node formNode, String location) {
        this.formNode = formNode
        this.location = location

        // prep row-actions
        if (formNode."row-actions") {
            rowActions = new XmlAction(ecfi, (Node) formNode."row-actions"[0], location + ".row-actions")
        }

        // TODO handle auto-fields-service
        // TODO handle auto-fields-entity

        // TODO prep fields (use ScreenWidget, or a different class for fields?

        // TODO handle field-layout
    }

    void renderSingle(ScreenRenderImpl sri) {

    }

    void renderListRow(ScreenRenderImpl sri) {

        // TODO if list or multi, iterate and run rowActions
        if (rowActions) rowActions.run(sri.ec)

        // TODO if list or multi and list is an ELI, close it
    }
}
