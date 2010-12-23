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
package org.moqui.impl

/** These are utilities that should exist elsewhere, but I can't find a good simple library for them, and they are
 * stupid but necessary for certain things. 
 */
class StupidUtilities {

    protected static final Map<String, Class> commonJavaClassesMap = [
            "java.lang.String":java.lang.String.class, "String":java.lang.String.class,
            "java.sql.Timestamp":java.sql.Timestamp.class, "Timestamp":java.sql.Timestamp.class,
            "java.sql.Time":java.sql.Time.class, "Time":java.sql.Time.class,
            "java.sql.Date":java.sql.Date.class, "Date":java.sql.Date.class,
            "java.lang.Integer":java.lang.Integer.class, "Integer":java.lang.Integer.class,
            "java.lang.Long":java.lang.Long.class,"Long":java.lang.Long.class,
            "java.lang.Float":java.lang.Float.class, "Float":java.lang.Float.class,
            "java.lang.Double":java.lang.Double.class, "Double":java.lang.Double.class,
            "java.math.BigDecimal":java.math.BigDecimal.class, "BigDecimal":java.math.BigDecimal.class,
            "java.lang.Boolean":java.lang.Boolean.class, "Boolean":java.lang.Boolean.class,
            "java.lang.Object":java.lang.Object.class, "Object":java.lang.Object.class,
            "java.sql.Blob":java.sql.Blob.class, "Blob":java.sql.Blob.class,
            "java.nio.ByteBuffer":java.nio.ByteBuffer.class,
            "java.sql.Clob":java.sql.Clob.class, "Clob":java.sql.Clob.class,
            "java.util.Date":java.util.Date.class,
            "java.util.Collection":java.util.Collection.class,
            "java.util.List":java.util.List.class,
            "java.util.Map":java.util.Map.class,
            "java.util.Set":java.util.Set.class]

    static boolean isInstanceOf(Object theObjectInQuestion, String javaType) {
        Class theClass = commonJavaClassesMap.get(javaType)
        if (!theClass) theClass = StupidUtilities.class.getClassLoader().loadClass(javaType)
        if (!theClass) theClass = System.getClassLoader().loadClass(javaType)
        if (!theClass) throw new IllegalArgumentException("Cannot find class for type: ${javaType}")
        return theClass.isInstance(theObjectInQuestion)
    }
}
