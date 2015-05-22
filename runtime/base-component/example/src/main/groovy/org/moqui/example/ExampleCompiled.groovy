/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.example

class ExampleCompiled {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ExampleCompiled.class)

    static void testMethod() { logger.warn("The testMethod was called!") }
    static String echo(String input) { input }
}
