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

import java.security.MessageDigest
import java.sql.Timestamp
import java.util.regex.Pattern

import org.apache.commons.codec.binary.Hex
import org.w3c.dom.Element
import java.nio.charset.Charset
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** These are utilities that should exist elsewhere, but I can't find a good simple library for them, and they are
 * stupid but necessary for certain things. 
 */
class StupidUtilities {
    protected final static Logger logger = LoggerFactory.getLogger(StupidUtilities.class)

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

    static final boolean compareLike(Object value1, Object value2) {
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

    static boolean compare(Object field, String operator, String value, Object toField, String format, String type) {
        toField = toField ?: value

        if (format) {
            // TODO handle type conversion with format for Date, Time, Timestamp
        }

        switch (type) {
            case "String": field = field as String; toField = toField as String; break;
            case "BigDecimal": field = field as BigDecimal; toField = toField as BigDecimal; break;
            case "Double": field = field as Double; toField = toField as Double; break;
            case "Float": field = field as Double; toField = toField as Double; break;
            case "Long": field = field as Long; toField = toField as Long; break;
            case "Integer": field = field as Long; toField = toField as Long; break;
            case "Date": field = field as java.sql.Date; toField = toField as java.sql.Date; break;
            case "Time": field = field as java.sql.Time; toField = toField as java.sql.Time; break;
            case "Timestamp": field = field as java.sql.Timestamp; toField = toField as java.sql.Timestamp; break;
            case "Boolean": field = field as Boolean; toField = toField as Boolean; break;
            case "Object":
            default: break; // do nothing for Object or by default
        }

        boolean result
        switch (operator) {
            case "less": result = (field < toField); break;
            case "greater": result = (field > toField); break;
            case "less-equals": result = (field <= toField); break;
            case "greater-equals": result = (field >= toField); break;

            case "contains": result = (field as String).contains(toField as String); break;
            case "not-contains": result = !(field as String).contains(toField as String); break;

            case "empty": result = (field ? false : true); break;
            case "not-empty": result = (field ? true : false); break;

            case "matches": result = (field as String).matches(toField as String); break;
            case "not-matches": result = !(field as String).matches(toField as String); break;

            case "not-equals": result = (field != toField); break;
            case "equals":
            default: result = (field == toField)
            break;
        }

        if (logger.traceEnabled) logger.trace("Compare result [${result}] for field [${field}] operator [${operator}] value [${value}] toField [${toField}] type [${type}]")
        return result
    }

    static void filterMapList(List<Map> theList, Map<String, Object> fieldValues) {
        if (!theList || !fieldValues) return
        Iterator<Map> theIterator = theList.iterator()
        while (theIterator.hasNext()) {
            Map curMap = theIterator.next()
            for (Map.Entry entry in fieldValues.entrySet()) {
                if (curMap.get(entry.key) != entry.value) { theIterator.remove(); break }
            }
        }
    }

    static void filterMapListByDate(List<Map> theList, String fromDateName, String thruDateName, Timestamp compareStamp) {
        if (!theList) return
        if (!fromDateName) fromDateName = "fromDate"
        if (!thruDateName) thruDateName = "thruDate"
        // no access to ec.user here, so this should always be passed in, but just in case
        if (!compareStamp) compareStamp = new Timestamp(System.currentTimeMillis())

        Iterator<Map> theIterator = theList.iterator()
        while (theIterator.hasNext()) {
            Map curMap = theIterator.next()
            Timestamp fromDate = curMap.get(fromDateName) as Timestamp
            if (fromDate && compareStamp < fromDate) { theIterator.remove(); continue }
            Timestamp thruDate = curMap.get(thruDateName) as Timestamp
            if (thruDate && compareStamp >= thruDate) theIterator.remove();
        }
    }

    static void orderMapList(List<Map> theList, List<String> fieldNames) {
        if (theList && fieldNames) Collections.sort(theList, new MapOrderByComparator(fieldNames))
    }

    static class MapOrderByComparator implements Comparator<Map> {
        protected List<String> fieldNameList = new ArrayList<String>()

        public MapOrderByComparator(List<String> fieldNameList) { this.fieldNameList = fieldNameList }

        @Override
        public int compare(Map map1, Map map2) {
            for (String fieldName in this.fieldNameList) {
                boolean ascending = true
                if (fieldName.charAt(0) == '-') {
                    ascending = false
                    fieldName = fieldName.substring(1)
                } else if (fieldName.charAt(0) == '+') {
                    fieldName = fieldName.substring(1)
                }
                Comparable value1 = (Comparable) map1.get(fieldName)
                Comparable value2 = (Comparable) map2.get(fieldName)
                // NOTE: nulls go earlier in the list for ascending, later in the list for !ascending
                if (value1 == null) {
                    if (value2 != null) return ascending ? 1 : -1
                } else {
                    if (value2 == null) {
                        return ascending ? -1 : 1
                    } else {
                        int comp = value1.compareTo(value2)
                        if (comp != 0) return ascending ? comp : -comp
                    }
                }
            }
            // all evaluated to 0, so is the same, so return 0
            return 0
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof MapOrderByComparator)) return false
            return this.fieldNameList.equals(((MapOrderByComparator) obj).fieldNameList)
        }

        @Override
        public String toString() { return this.fieldNameList.toString() }
    }

    static int countChars(String s, boolean countDigits, boolean countLetters, boolean countOthers) {
        // this seems like it should be part of some standard Java API, but I haven't found it
        // (can use Pattern/Matcher, but that is even uglier and probably a lot slower)
        int count = 0
        for (char c in s) {
            if (Character.isDigit(c)) {
                if (countDigits) count++
            } else if (Character.isLetter(c)) {
                if (countLetters) count++
            } else {
                if (countOthers) count++
            }
        }
        return count
    }

    static int countChars(String s, char cMatch) {
        int count = 0
        for (char c in s) if (c == cMatch) count++
        return count
    }

    static String getStreamText(InputStream is) {
        if (!is) return null

        Reader r = null
        try {
            r = new InputStreamReader(new BufferedInputStream(is), Charset.forName("UTF-8"))

            StringBuilder sb = new StringBuilder()
            char[] buf = new char[4096]
            int i
            while ((i = r.read(buf, 0, 4096)) > 0) {
                sb.append(buf, 0, i)
            }
            return sb.toString()
        } finally {
            // closing r should close is, if not add that here
            try { if (r) r.close() } catch (IOException e) { logger.warn("Error in close after reading text", e) }
        }
    }

    static void addToListInMap(String key, Object value, Map theMap) {
        if (!theMap) return
        List theList = (List) theMap.get(key)
        if (!theList) {
            theList = new ArrayList()
            theMap.put(key, theList)
        }
        theList.add(value)
    }

    static Node deepCopyNode(Node original) {
        // always pass in a null parent and expect this to be appended to the parent node by the caller if desired
        Node newNode = new Node(null, original.name(), original.attributes())
        for (Node childNode in original.children()) newNode.append(deepCopyNode(childNode))
        return newNode
    }

    static String elementValue(Element element) {
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

    static String encodeForXmlAttribute(String original) {
        StringBuilder newValue = new StringBuilder(original)
        for (int i = 0; i < newValue.length(); i++) {
            char curChar = newValue.charAt(i)

            switch (curChar) {
            case '\'': newValue.replace(i, i+1, "&apos;"); break;
            case '"' : newValue.replace(i, i+1, "&quot;"); break;
            case '&' : newValue.replace(i, i+1, "&amp;"); break;
            case '<' : newValue.replace(i, i+1, "&lt;"); break;
            case '>' : newValue.replace(i, i+1, "&gt;"); break;
            case 0x5 : newValue.replace(i, i+1, "..."); break;
            case 0x12: newValue.replace(i, i+1, "&apos;"); break;
            case 0x13: newValue.replace(i, i+1, "&quot;"); break; // left
            case 0x14: newValue.replace(i, i+1, "&quot;"); break; // right
            case 0x16: newValue.replace(i, i+1, "-"); break; // big dash
            case 0x17: newValue.replace(i, i+1, "-"); break;
            case 0x19: newValue.replace(i, i+1, "tm"); break;
            default:
                if (curChar < 0x20 && curChar != 0x9 && curChar != 0xA && curChar != 0xD) {
                    // the only valid values < 0x20 are 0x9 (tab), 0xA (newline), 0xD (carriage return)
                    newValue.deleteCharAt(i)
                } else if (curChar > 0x7F) {
                    // Replace each char which is out of the ASCII range with a XML entity
                    newValue.replace(i, i+1, "&#" + (int) curChar + ";")
                }
            }
        }
        return newValue.toString()
    }

    public static String paddedNumber(long number, Integer desiredLength) {
        StringBuilder outStrBfr = new StringBuilder(Long.toString(number))
        if (!desiredLength) return outStrBfr.toString()
        while (desiredLength > outStrBfr.length()) outStrBfr.insert(0, '0')
        return outStrBfr.toString()
    }

    public static String getHashDigest(String key, String salt, String hashType) {
        if (!key) return null
        // NOTE: if salt is an empty String then there will effectively be no salt
        if (salt == null) salt = paddedNumber(Math.round(Math.random() * 99999999L), 8)
        if (!hashType) hashType = "SHA"
        try {
            String str = salt + key
            MessageDigest md = MessageDigest.getInstance(hashType)
            md.update(str.getBytes())
            char[] digestChars = Hex.encodeHex(md.digest())

            StringBuilder sb = new StringBuilder()
            sb.append("{").append(hashType).append("}")
            sb.append(salt).append(":")
            sb.append(digestChars, 0, digestChars.length)
            return sb.toString()
        } catch (Exception e) {
            throw new IllegalArgumentException("Error while computing hash of type [${hashType}]", e)
        }
    }

    public static String getHashTypeFromFull(String hashString) {
        if (!hashString || hashString.charAt(0) != '{')  return ""
        return hashString.substring(1, hashString.indexOf('}'))
    }
    public static String getHashSaltFromFull(String hashString) {
        // NOTE: return empty String instead of null because the getHashDigest method will treat that as no salt instead of generating a salt value
        if (!hashString)  return ""
        if (!hashString.contains(":"))  return ""
        int closeBraceIndex = hashString.indexOf('}')
        if (closeBraceIndex > 0) {
            return hashString.substring(closeBraceIndex + 1, hashString.indexOf(':'))
        } else {
            return hashString.substring(0, hashString.indexOf(':'))
        }
    }
    public static String getHashHashFromFull(String hashString) {
        if (!hashString)  return ""
        if (hashString.contains(":")) return hashString.substring(hashString.indexOf(':') + 1)
        if (hashString.contains("}")) return hashString.substring(hashString.indexOf('}') + 1)
        return hashString
    }
}
