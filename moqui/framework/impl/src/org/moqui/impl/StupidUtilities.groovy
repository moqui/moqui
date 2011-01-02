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

import org.moqui.entity.EntityCondition.ComparisonOperator
import org.moqui.entity.EntityCondition.JoinOperator
import java.util.regex.Pattern
import java.sql.Connection
import org.w3c.dom.Element

/** These are utilities that should exist elsewhere, but I can't find a good simple library for them, and they are
 * stupid but necessary for certain things. 
 */
class StupidUtilities {

    protected static final Map<String, Class<?>> commonJavaClassesMap = [
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


    public static final Map<ComparisonOperator, String> comparisonOperatorStringMap = new HashMap()
    static {
        comparisonOperatorStringMap.put(ComparisonOperator.EQUALS, "=")
        comparisonOperatorStringMap.put(ComparisonOperator.NOT_EQUAL, "<>")
        comparisonOperatorStringMap.put(ComparisonOperator.LESS_THAN, "<")
        comparisonOperatorStringMap.put(ComparisonOperator.GREATER_THAN, ">")
        comparisonOperatorStringMap.put(ComparisonOperator.LESS_THAN_EQUAL_TO, "<=")
        comparisonOperatorStringMap.put(ComparisonOperator.GREATER_THAN_EQUAL_TO, ">=")
        comparisonOperatorStringMap.put(ComparisonOperator.IN, "IN")
        comparisonOperatorStringMap.put(ComparisonOperator.NOT_IN, "NOT IN")
        comparisonOperatorStringMap.put(ComparisonOperator.BETWEEN, "BETWEEN")
        comparisonOperatorStringMap.put(ComparisonOperator.LIKE, "LIKE")
        comparisonOperatorStringMap.put(ComparisonOperator.NOT_LIKE, "NOT LIKE")
    }
    public static final Map<String, ComparisonOperator> stringComparisonOperatorMap = [
            "=":ComparisonOperator.EQUALS,
            "equals":ComparisonOperator.EQUALS,

            "not-equals":ComparisonOperator.NOT_EQUAL,
            "not-equal":ComparisonOperator.NOT_EQUAL,
            "!=":ComparisonOperator.NOT_EQUAL,
            "<>":ComparisonOperator.NOT_EQUAL,

            "less-than":ComparisonOperator.LESS_THAN,
            "less":ComparisonOperator.LESS_THAN,
            "<":ComparisonOperator.LESS_THAN,

            "greater-than":ComparisonOperator.GREATER_THAN,
            "greater":ComparisonOperator.GREATER_THAN,
            ">":ComparisonOperator.GREATER_THAN,

            "less-than-equal-to":ComparisonOperator.LESS_THAN_EQUAL_TO,
            "less-equals":ComparisonOperator.LESS_THAN_EQUAL_TO,
            "<=":ComparisonOperator.LESS_THAN_EQUAL_TO,

            "greater-than-equal-to":ComparisonOperator.GREATER_THAN_EQUAL_TO,
            "greater-equals":ComparisonOperator.GREATER_THAN_EQUAL_TO,
            ">=":ComparisonOperator.GREATER_THAN_EQUAL_TO,

            "in":ComparisonOperator.IN,
            "IN":ComparisonOperator.IN,

            "not-in":ComparisonOperator.NOT_IN,
            "NOT IN":ComparisonOperator.NOT_IN,

            "between":ComparisonOperator.BETWEEN,
            "BETWEEN":ComparisonOperator.BETWEEN,

            "like":ComparisonOperator.LIKE,
            "LIKE":ComparisonOperator.LIKE,

            "not-like":ComparisonOperator.LIKE,
            "NOT LIKE":ComparisonOperator.NOT_LIKE
    ]

    public static String getJoinOperatorString(JoinOperator op) {
        return op == JoinOperator.OR ? "OR" : "AND"
    }
    public static String getComparisonOperatorString(ComparisonOperator op) {
        return comparisonOperatorStringMap.get(op)
    }
    public static ComparisonOperator getComparisonOperator(String opName) {
        return stringComparisonOperatorMap.get(opName)
    }

    public static boolean compareByOperator(Object value1, ComparisonOperator op, Object value2) {
        switch (op) {
        case ComparisonOperator.EQUALS:
            return value1 == value2
        case ComparisonOperator.NOT_EQUAL:
            return value1 != value2
        case ComparisonOperator.LESS_THAN:
            return value1 < value2
        case ComparisonOperator.GREATER_THAN:
            return value1 > value2
        case ComparisonOperator.LESS_THAN_EQUAL_TO:
            return value1 <= value2
        case ComparisonOperator.GREATER_THAN_EQUAL_TO:
            return value1 >= value2
        case ComparisonOperator.IN:
            if (value2 instanceof Collection) {
                return ((Collection) value2).contains(value1)
            } else {
                // not a Collection, try equals
                return value1 == value2
            }
        case ComparisonOperator.NOT_IN:
            if (value2 instanceof Collection) {
                return !((Collection) value2).contains(value1)
            } else {
                // not a Collection, try not-equals
                return value1 != value2
            }
        case ComparisonOperator.BETWEEN:
            if (value2 instanceof Collection && ((Collection) value2).size() == 2) {
                Iterator iterator = ((Collection) value2).iterator()
                Object lowObj = iterator.next()
                Object highObj = iterator.next()
                return lowObj <= value1 && value1 < highObj
            } else {
                return false
            }
        case ComparisonOperator.LIKE:
            return compareLike(value1, value2)
        case ComparisonOperator.NOT_LIKE:
            return !compareLike(value1, value2)
        }
        // default return false
        return false
    }

    public static final boolean compareLike(Object value1, Object value2) {
        // nothing to be like? consider a match
        if (!value2) return true
        // something to be like but nothing to compare? consider a mismatch
        if (!value1) return false
        if (value1 instanceof String && value2 instanceof String) {
            // first escape the characters that would be interpreted as part of the regular expression
            int length2 = value2.length()
            StringBuilder sb = new StringBuilder(length2 * 2)
            for (int i = 0; i < length2; i++) {
                char c = value2.charAt(i)
                if ("[](){}.*+?\$^|#\\".indexOf(c) != -1) {
                    sb.append("\\")
                }
                sb.append(c)
            }
            // change the SQL wildcards to regex wildcards
            String regex = sb.toString().replace("_", ".").replace("%", ".*?")
            // run it...
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
            return pattern.matcher(value1).matches()
        } else {
            return false
        }
    }

    public static int getTxIsolationFromString(String isolationLevel) {
        if (!isolationLevel) return -1
        if ("Serializable".equals(isolationLevel)) {
            return Connection.TRANSACTION_SERIALIZABLE
        } else if ("RepeatableRead".equals(isolationLevel)) {
            return Connection.TRANSACTION_REPEATABLE_READ
        } else if ("ReadUncommitted".equals(isolationLevel)) {
            return Connection.TRANSACTION_READ_UNCOMMITTED
        } else if ("ReadCommitted".equals(isolationLevel)) {
            return Connection.TRANSACTION_READ_COMMITTED
        } else if ("None".equals(isolationLevel)) {
            return Connection.TRANSACTION_NONE
        } else {
            return -1
        }
    }

    public static void addToListInMap(String key, Object value, Map theMap) {
        if (!theMap) return
        List theList = (List) theMap.get(key)
        if (!theList) {
            theList = new ArrayList()
            theMap.put(key, theList)
        }
        theList.add(value)
    }

    public static String elementValue(Element element) {
        if (element == null) return null
        element.normalize()
        org.w3c.dom.Node textNode = element.getFirstChild()
        if (textNode == null) return null

        StringBuilder value = new StringBuilder()
        if (textNode.getNodeType() == org.w3c.dom.Node.CDATA_SECTION_NODE || textNode.getNodeType() == org.w3c.dom.Node.TEXT_NODE)
            value.append(textNode.getNodeValue())
        while ((textNode = textNode.getNextSibling()) != null) {
            if (textNode.getNodeType() == org.w3c.dom.Node.CDATA_SECTION_NODE || textNode.getNodeType() == org.w3c.dom.Node.TEXT_NODE)
                value.append(textNode.getNodeValue())
        }
        return value.toString()
    }
}
