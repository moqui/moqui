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
package org.moqui.entity;

import java.io.Writer;
import java.util.Iterator;
import java.util.ListIterator;

/**
 * Entity Cursor List Iterator for Handling Cursored Database Results
 */
public interface EntityListIterator extends ListIterator<EntityValue>, Iterable<EntityValue> {
    /** Close the underlying ResultSet and Connection. This should ALWAYS be called when you are done with an EntityListIterator object. */
    void close() throws EntityException;

    /** Sets the cursor position to just after the last result so that previous() will return the last result */
    void afterLast() throws EntityException;

    /** Sets the cursor position to just before the first result so that next() will return the first result */
    void beforeFirst() throws EntityException;

    /** Sets the cursor position to last result; if result set is empty returns false */
    boolean last() throws EntityException;

    /** Sets the cursor position to last result; if result set is empty returns false */
    boolean first() throws EntityException;

    /** NOTE: Calling this method does return the current value, but so does calling next() or previous(), so calling
     * one of those AND this method will cause the value to be created twice
     */
    EntityValue currentEntityValue() throws EntityException;

    int currentIndex() throws EntityException;

    /** performs the same function as the ResultSet.absolute method;
     * if rowNum is positive, goes to that position relative to the beginning of the list;
     * if rowNum is negative, goes to that position relative to the end of the list;
     * a rowNum of 1 is the same as first(); a rowNum of -1 is the same as last()
     */
    boolean absolute(int rowNum) throws EntityException;

    /** performs the same function as the ResultSet.relative method;
     * if rows is positive, goes forward relative to the current position;
     * if rows is negative, goes backward relative to the current position;
     */
    boolean relative(int rows) throws EntityException;

    /**
     * PLEASE NOTE: Because of the nature of the JDBC ResultSet interface this method can be very inefficient; it is
     * much better to just use next() until it returns null.
     *
     * For example, you could use the following to iterate through the results in an EntityListIterator:
     *
     *      EntityValue nextValue = null;
     *      while ((nextValue = (EntityValue) this.next()) != null) { ... }
     *
     */
    boolean hasNext();

    /** PLEASE NOTE: Because of the nature of the JDBC ResultSet interface this method can be very inefficient; it is
     * much better to just use previous() until it returns null.
     */
    boolean hasPrevious();

    /** Moves the cursor to the next position and returns the EntityValue object for that position; if there is no next,
     * returns null.
     *
     * For example, you could use the following to iterate through the results in an EntityListIterator:
     *
     *      EntityValue nextValue = null;
     *      while ((nextValue = (EntityValue) this.next()) != null) { ... }
     *
     */
    EntityValue next();

    /** Returns the index of the next result, but does not guarantee that there will be a next result */
    int nextIndex();

    /** Moves the cursor to the previous position and returns the EntityValue object for that position; if there is no
     * previous, returns null.
     */
    EntityValue previous();

    /** Returns the index of the previous result, but does not guarantee that there will be a previous result */
    int previousIndex();

    void setFetchSize(int rows) throws EntityException;

    EntityList getCompleteList(boolean closeAfter) throws EntityException;

    /** Gets a partial list of results starting at start and containing at most number elements.
     * Start is a one based value, ie 1 is the first element.
     */
    EntityList getPartialList(int offset, int limit, boolean closeAfter) throws EntityException;

    /** Writes XML text with an attribute or CDATA element for each field of each record. If dependents is true also
     * writes all dependent (descendant) records.
     * @param writer A Writer object to write to
     * @param prefix A prefix to put in front of the entity name in the tag name
     * @param dependents Write dependent (descendant) records as well?
     * @return The number of records written
     */
    int writeXmlText(Writer writer, String prefix, boolean dependents);

    /** Method to implement the Iterable interface to allow an EntityListIterator to be used in a foreach loop. Just
     * returns this.
     */
    Iterator<EntityValue> iterator();
}
