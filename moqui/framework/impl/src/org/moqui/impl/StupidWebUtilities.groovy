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

import javax.servlet.ServletRequest
import javax.servlet.http.HttpSession
import javax.servlet.ServletContext

import org.owasp.esapi.codecs.Codec
import org.owasp.esapi.codecs.HTMLEntityCodec
import org.owasp.esapi.codecs.PercentCodec
import org.owasp.esapi.reference.DefaultEncoder
import org.owasp.esapi.Encoder

class StupidWebUtilities {
    public static Map<String, Object> getPathInfoParameterMap(String pathInfoStr) {
        Map<String, Object> paramMap = new HashMap()

        // add in all path info parameters /~name1=value1/~name2=value2/
        if (pathInfoStr) {
            if (!pathInfoStr.endsWith("/")) pathInfoStr += "/"
            int current = pathInfoStr.indexOf('/')
            int last = current
            while ((current = pathInfoStr.indexOf('/', last + 1)) != -1) {
                String element = pathInfoStr.substring(last + 1, current)
                last = current
                if (element.charAt(0) == '~' && element.contains('=')) {
                    String name = element.substring(1, element.indexOf('='))
                    String value = element.substring(element.indexOf('=') + 1)
                    // NOTE: currently ignoring existing values, likely won't be any: Object curValue = paramMap.get(name)
                    paramMap.put(name, value)
                }
            }
        }

        return paramMap
    }

    static class SimpleEntry implements Map.Entry<String, Object> {
        protected String key
        protected Object value
        SimpleEntry(String key, Object value) { this.key = key; this.value = value; }
        String getKey() { return key }
        Object getValue() { return value }
        Object setValue(Object v) { Object orig = value; value = v; return orig; }
    }
    
    static class RequestAttributeMap implements Map<String, Object> {
        protected ServletRequest req
        RequestAttributeMap(ServletRequest request) { req = request }
        int size() { return req.getAttributeNames().toList().size() }
        boolean isEmpty() { return req.getAttributeNames().toList().size() > 0 }
        boolean containsKey(Object o) { return req.getAttributeNames().toList().contains(o) }
        boolean containsValue(Object o) {
            for (String name in req.getAttributeNames()) if (req.getAttribute(name) == o) return true
            return false
        }
        Object get(Object o) { return req.getAttribute((String) o) }
        Object put(String s, Object o) { Object orig = req.getAttribute(s); req.setAttribute(s, o); return orig; }
        Object remove(Object o) { Object orig = req.getAttribute((String) o); req.removeAttribute((String) o); return orig; }
        void putAll(Map<? extends String, ? extends Object> map) {
            if (!map) return
            for (Map.Entry entry in map.entrySet()) req.setAttribute((String) entry.getKey(), entry.getValue())
        }
        void clear() { for (String name in req.getAttributeNames()) req.removeAttribute(name) }
        Set<String> keySet() { Set<String> ks = new HashSet<String>(); ks.addAll(req.getAttributeNames().toList()); return ks; }
        Collection<Object> values() { List values = new LinkedList(); for (String name in req.getAttributeNames()) values.add(req.getAttribute(name)); return values; }
        Set<Map.Entry<String, Object>> entrySet() {
            Set<Map.Entry<String, Object>> es = new HashSet<Map.Entry<String,Object>>()
            for (String name in req.getAttributeNames()) es.add(new SimpleEntry(name, req.getAttribute(name)))
            return es
        }
    }

    static class SessionAttributeMap implements Map<String, Object> {
        protected HttpSession ses
        SessionAttributeMap(HttpSession session) { ses = session }
        int size() { return ses.getAttributeNames().toList().size() }
        boolean isEmpty() { return ses.getAttributeNames().toList().size() > 0 }
        boolean containsKey(Object o) { return ses.getAttributeNames().toList().contains(o) }
        boolean containsValue(Object o) {
            for (String name in ses.getAttributeNames()) if (ses.getAttribute(name) == o) return true
            return false
        }
        Object get(Object o) { return ses.getAttribute((String) o) }
        Object put(String s, Object o) { Object orig = ses.getAttribute(s); ses.setAttribute(s, o); return orig; }
        Object remove(Object o) { Object orig = ses.getAttribute((String) o); ses.removeAttribute((String) o); return orig; }
        void putAll(Map<? extends String, ? extends Object> map) {
            if (!map) return
            for (Map.Entry entry in map.entrySet()) ses.setAttribute((String) entry.getKey(), entry.getValue())
        }
        void clear() { for (String name in ses.getAttributeNames()) ses.removeAttribute(name) }
        Set<String> keySet() { Set<String> ks = new HashSet<String>(); ks.addAll(ses.getAttributeNames().toList()); return ks; }
        Collection<Object> values() { List values = new LinkedList(); for (String name in ses.getAttributeNames()) values.add(ses.getAttribute(name)); return values; }
        Set<Map.Entry<String, Object>> entrySet() {
            Set<Map.Entry<String, Object>> es = new HashSet<Map.Entry<String,Object>>()
            for (String name in ses.getAttributeNames()) es.add(new SimpleEntry(name, ses.getAttribute(name)))
            return es
        }
    }

    static class ServletContextAttributeMap implements Map<String, Object> {
        protected ServletContext sc
        ServletContextAttributeMap(ServletContext servletContext) { sc = servletContext }
        int size() { return sc.getAttributeNames().toList().size() }
        boolean isEmpty() { return sc.getAttributeNames().toList().size() > 0 }
        boolean containsKey(Object o) { return sc.getAttributeNames().toList().contains(o) }
        boolean containsValue(Object o) {
            for (String name in sc.getAttributeNames()) if (sc.getAttribute(name) == o) return true
            return false
        }
        Object get(Object o) { return sc.getAttribute((String) o) }
        Object put(String s, Object o) { Object orig = sc.getAttribute(s); sc.setAttribute(s, o); return orig; }
        Object remove(Object o) { Object orig = sc.getAttribute((String) o); sc.removeAttribute((String) o); return orig; }
        void putAll(Map<? extends String, ? extends Object> map) {
            if (!map) return
            for (Map.Entry entry in map.entrySet()) sc.setAttribute((String) entry.getKey(), entry.getValue())
        }
        void clear() { for (String name in sc.getAttributeNames()) sc.removeAttribute(name) }
        Set<String> keySet() { Set<String> ks = new HashSet<String>(); ks.addAll(sc.getAttributeNames().toList()); return ks; }
        Collection<Object> values() { List values = new LinkedList(); for (String name in sc.getAttributeNames()) values.add(sc.getAttribute(name)); return values; }
        Set<Map.Entry<String, Object>> entrySet() {
            Set<Map.Entry<String, Object>> es = new HashSet<Map.Entry<String,Object>>()
            for (String name in sc.getAttributeNames()) es.add(new SimpleEntry(name, sc.getAttribute(name)))
            return es
        }
    }
    
    static final Encoder defaultWebEncoder = DefaultEncoder.getInstance()

    static class CanonicalizeMap implements Map<String, Object> {
        protected Map mp
        protected boolean supportsNull = true
        CanonicalizeMap(Map map) {
            mp = map
            if (mp instanceof Hashtable) supportsNull = false
        }
        int size() { return mp.size() }
        boolean isEmpty() { return mp.isEmpty() }
        boolean containsKey(Object o) { return (o == null && !supportsNull) ? false : mp.containsKey(o) }
        boolean containsValue(Object o) { return mp.containsValue(o) }
        Object get(Object o) {
            return (o == null && !supportsNull) ? null : StupidWebUtilities.canonicalizeValue(mp.get(o))
        }
        Object put(String k, Object v) {
            return StupidWebUtilities.canonicalizeValue(mp.put(k, v))
        }
        Object remove(Object o) {
            return (o == null && !supportsNull) ? null : StupidWebUtilities.canonicalizeValue(mp.remove(o))
        }
        void putAll(Map<? extends String, ? extends Object> map) { if (map) mp.putAll(map) }
        void clear() { mp.clear() }
        Set<String> keySet() { return mp.keySet() }
        Collection<Object> values() {
            List<Object> values = new ArrayList<Object>(mp.size())
            for (Object orig in mp.values()) values.add(canonicalizeValue(orig))
            return values
        }
        Set<Map.Entry<String, Object>> entrySet() {
            Set<Map.Entry<String, Object>> es = new HashSet<Map.Entry<String, Object>>()
            for (Map.Entry<String, Object> entry in mp.entrySet()) es.add(new CanonicalizeEntry(entry))
            return es
        }
    }

    static class CanonicalizeEntry implements Map.Entry<String, Object> {
        protected String key
        protected Object value
        CanonicalizeEntry(String key, Object value) { this.key = key; this.value = value; }
        CanonicalizeEntry(Map.Entry<String, Object> entry) { this.key = entry.getKey(); this.value = entry.getValue(); }
        String getKey() { return key }
        Object getValue() {
            return StupidWebUtilities.canonicalizeValue(value)
        }
        Object setValue(Object v) { Object orig = value; value = v; return orig; }
    }

    protected static Object canonicalizeValue(Object orig) {
        if (orig instanceof List || orig instanceof String[] || orig instanceof Object[]) {
            List lst = orig as List
            if (lst.size() == 1) {
                orig = lst.get(0)
            } else if (lst.size() > 1) {
                orig = new ArrayList(lst.size())
                for (Object obj in lst) {
                    if (obj instanceof String) orig.add(defaultWebEncoder.canonicalize(obj, false))
                }
            }
        }
        if (orig instanceof String) orig = defaultWebEncoder.canonicalize(orig, false)
        return orig
    }
}
