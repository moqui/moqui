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
package org.moqui.impl;

/** The methods in this class may look funny, but are implemented in Java instead of Groovy for performance reasons. */
public class StupidJavaUtilities extends ClassLoader {
    public static boolean internedStringsEqual(String s1, String s2) {
        if (s1 == null) {
            return (s2 == null);
        } else {
            // NOTE: the == is used here intentionally since the Strings passed in should be intern()'ed
            return s2 != null && (s1 == s2);
        }
    }
    public static boolean internedNonNullStringsEqual(String s1, String s2) { return (s1 == s2); }
}
