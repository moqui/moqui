/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */

import spock.lang.*

import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityValue
import org.moqui.Moqui
import org.moqui.context.ResourceReference

class ResourceFacadeTests extends Specification {
    @Shared
    ExecutionContext ec

    def setupSpec() {
        // init the framework, get the ec
        ec = Moqui.getExecutionContext()
    }

    def cleanupSpec() {
        ec.destroy()
    }

    @Unroll
    def "get Location ResourceReference (#location)"() {
        expect:
        ResourceReference rr = ec.resource.getLocationReference(location)
        // the resolved location is unpredictable, so don't test for that: rr.location == location
        rr.uri.scheme == scheme
        rr.uri.host == host
        rr.fileName == fileName
        rr.contentType == contentType
        (!rr.supportsExists() || rr.exists) == exists
        (!rr.supportsDirectory() || rr.file) == isFile
        (!rr.supportsDirectory() || rr.directory) == isDirectory

        where:
        location | scheme | host | fileName | contentType | exists | isFile | isDirectory
        "component://example/screen/ExampleApp.xml" | "file" | null | "ExampleApp.xml" | "text/xml" | true | true | false
        "component://example/screen/ExampleAppFoo.xml" | "file" | null | "ExampleAppFoo.xml" | "text/xml" | false | false | false
        "classpath://entity/BasicEntities.xml" | "file" | null | "BasicEntities.xml" | "text/xml" | true | true | false
        "classpath://jta.properties" | "file" | null | "jta.properties" | "text/x-java-properties" | true | true | false
        "classpath://shiro.ini" | "file" | null | "shiro.ini" | "text/plain" | true | true | false
        "template/screen-macro/ScreenHtmlMacros.ftl" | "file" | null | "ScreenHtmlMacros.ftl" | "text/x-freemarker" | true | true | false
        "template/screen-macro" | "file" | null | "screen-macro" | null | true | false | true
    }

    @Unroll
    def "get Location Text (#location)"() {
        expect:
        String text = ec.resource.getLocationText(location, true)
        text.contains(contents)

        where:
        location | contents
        "component://example/screen/ExampleApp.xml" | "<subscreens default-item=\"Example\">"
        "classpath://shiro.ini" | "org.moqui.impl.MoquiShiroRealm"
    }

    // TODO: add tests for renderTemplateInCurrentContext and runScriptInCurrentContext

    @Unroll
    def "groovy evaluate Condition (#expression)"() {
        expect:
        result == ec.resource.evaluateCondition(expression, "")

        where:
        expression | result
        "true" | true
        "false" | false
        "ec.context instanceof org.moqui.context.ContextStack" | true
    }

    @Unroll
    def "groovy evaluate Context Field (#expression)"() {
        expect:
        result == ec.resource.evaluateContextField(expression, "")

        where:
        expression | result
        "ec.tenantId" | ec.tenantId
        "null" | null
        "undefinedVariable" | null
    }

    @Unroll
    def "groovy evaluate String Expand (#inputString)"() {
        expect:
        result == ec.resource.evaluateStringExpand(inputString, "")

        where:
        inputString | result
        "Tenant: \${ec.tenantId}" | "Tenant: ${ec.tenantId}"
        "plain string" | "plain string"
    }
}
