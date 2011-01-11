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
package org.moqui.impl.context

class ContextStack implements Map<String, Object> {
    protected Deque<Map<String, Object>> stackList = new LinkedList()

    public ContextStack() {
    }

    /** Puts a new Map on the top of the stack for a fresh local context
     * @return Returns reference to this ContextStack
     */
    public ContextStack push() {
        Map<String, Object> newMap = new HashMap()
        stackList.push(newMap)
        return this
    }

    /** Puts an existing Map on the top of the stack (top meaning will override lower layers on the stack) */
    public void push(Map<String, Object> existingMap) {
        if (existingMap == null) throw new IllegalArgumentException("Cannot push null as an existing Map")
        stackList.push(existingMap)
    }

    /** Remove and returns the Map from the top of the stack (the local context).
     * If there is only one Map on the stack it returns null and does not remove it.
     */
    public Map<String, Object> pop() { return stackList ? stackList.pop() : null }

    /** Add an existing Map as the Root Map, ie on the BOTTOM of the stack meaning it will be overridden by other Maps on the stack */
    public void addRootMap(Map<String, Object> existingMap) {
        if (existingMap == null) throw new IllegalArgumentException("Cannot add null as an existing Map ")
        stackList.add(existingMap)
    }

    public Map<String, Object> getRootMap() { return stackList.peekLast() }

    /**
     * Creates a ContextStack object that has the same Map objects on its stack (a shallow clone).
     * Meant to be used to enable a situation where a parent and child context are operating simultaneously using two
     * different ContextStack objects, but sharing the Maps between them.
     */
    public ContextStack clone() {
        ContextStack newStack = new ContextStack()
        newStack.stackList.addAll(stackList)
        return newStack
    }

    /** @see java.util.Map#size() */
    @Override
    public int size() {
        // use the keySet since this gets a set of all unique keys for all Maps in the stack
        Set<String> keys = keySet()
        return keys.size()
    }

    /** @see java.util.Map#isEmpty() */
    @Override
    public boolean isEmpty() {
        for (Map<String, Object> curMap in stackList) {
            if (!curMap.isEmpty()) return false
        }
        return true
    }

    /** @see java.util.Map#containsKey(java.lang.Object) */
    @Override
    public boolean containsKey(Object key) {
        for (Map<String, Object> curMap in stackList) {
            if (curMap.containsKey(key)) return true
        }
        return false
    }

    /** @see java.util.Map#containsValue(java.lang.Object) */
    @Override
    public boolean containsValue(Object value) {
        // this keeps track of keys looked at for values at each level of the stack so that the same key is not
        // considered more than once (the earlier Maps overriding later ones)
        Set keysObserved = new HashSet()
        for (Map<String, Object> curMap in stackList) {
            for (Map.Entry<String, Object> curEntry in curMap.entrySet()) {
                if (!keysObserved.contains(curEntry.getKey())) {
                    keysObserved.add(curEntry.getKey())
                    if (value == null) {
                        if (curEntry.getValue() == null) return true
                    } else {
                        if (value.equals(curEntry.getValue())) return true
                    }
                }
            }
        }
        return false
    }

    /** @see java.util.Map#get(java.lang.Object) */
    @Override
    public Object get(Object key) {
        for (Map<String, Object> curMap in stackList) {
            if (curMap.containsKey(key)) return curMap.get(key)
        }
        return null
    }

    /** @see java.util.Map#  */
    @Override
    public Object put(String key, Object value) {
        return stackList.peek().put(key, value)
    }

    /** @see java.util.Map#remove(java.lang.Object) */
    @Override
    public Object remove(Object key) {
        return stackList.peek().remove(key)
    }

    /** @see java.util.Map#putAll(java.util.Map) */
    @Override
    public void putAll(Map<? extends String, ? extends Object> arg0) { stackList.peek().putAll(arg0) }

    /** @see java.util.Map#clear() */
    @Override
    public void clear() { stackList.peek().clear() }

    /** @see java.util.Map#keySet() */
    @Override
    public Set<String> keySet() {
        Set<String> resultSet = new HashSet<String>()
        for (Map curMap in stackList) {
            resultSet.addAll(curMap.keySet())
        }
        return Collections.unmodifiableSet(resultSet)
    }

    /** @see java.util.Map#values() */
    @Override
    public Collection<Object> values() {
        Set keysObserved = new HashSet()
        List<Object> resultValues = new LinkedList<Object>()
        for (Map curMap in stackList) {
            for (Map.Entry<String, Object> curEntry in curMap.entrySet()) {
                if (!keysObserved.contains(curEntry.getKey())) {
                    keysObserved.add(curEntry.getKey())
                    resultValues.add(curEntry.getValue())
                }
            }
        }
        return Collections.unmodifiableCollection(resultValues)
    }

    /** @see java.util.Map#entrySet() */
    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
        Set keysObserved = new HashSet()
        Set<Map.Entry<String, Object>> resultEntrySet = new HashSet<Map.Entry<String, Object>>()
        for (Map curMap in stackList) {
            for (Map.Entry<String, Object> curEntry in curMap.entrySet()) {
                if (!keysObserved.contains(curEntry.getKey())) {
                    keysObserved.add(curEntry.getKey())
                    resultEntrySet.add(curEntry)
                }
            }
        }
        return Collections.unmodifiableSet(resultEntrySet)
    }

    @Override
    public String toString() {
        StringBuilder fullMapString = new StringBuilder()
        int curLevel = 0
        for (Map curMap in stackList) {
            fullMapString.append("============================== Start stack level ${curLevel}\n")
            for (Map.Entry<String, Object> curEntry in curMap.entrySet()) {
                fullMapString.append("==>[")
                fullMapString.append(curEntry.getKey())
                fullMapString.append("]:")
                if (curEntry.getValue() instanceof ContextStack) {
                    // skip instances of ContextStack to avoid infinite recursion
                    fullMapString.append("<Instance of ContextStack, not printing to avoid infinite recursion>")
                } else {
                    fullMapString.append(curEntry.getValue())
                }
                fullMapString.append("\n")
            }
            fullMapString.append("============================== End stack level ${curLevel}\n")
            curLevel++
        }
        return fullMapString.toString()
    }
}
