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

import java.util.List;
import java.util.Set;

/** Used to load XML entity data into the database or into an EntityList. The XML can come from
 * a specific location, XML text already read from somewhere, or by searching all component data directories
 * and the entity-facade.load-data elements for XML entity data files that match a type in the Set of types
 * specified.
 *
 * The document should have a root element like <code>&lt;entity-facade-xml type=&quot;seed&quot;&gt;</code>. The
 * type attribute will be used to determine if the file should be loaded by whether or not it matches the values
 * specified for data types on the loader.
 */
public interface EntityDataLoader {

    /** Location of the data file to load. Can be called multiple times to load multiple files.
     * @return Reference to this for convenience.
     */
    EntityDataLoader location(String location);
    /** List of locations of files to load. Will be added to running list, so can be called multiple times and along
     * with the location() method too.
     * @return Reference to this for convenience.
     */
    EntityDataLoader locationList(List<String> locationList);

    /** String with XML text in it, ready to be parsed.
     * @return Reference to this for convenience.
     */
    EntityDataLoader xmlText(String xmlText);
    EntityDataLoader csvText(String csvText);

    /** A Set of data types to match against the candidate files from the component data directories and the
     * entity-facade.load-data elements.
     * @return Reference to this for convenience.
     */
    EntityDataLoader dataTypes(Set<String> dataTypes);

    /** The transaction timeout for this data load in seconds. Defaults to 3600 which is 1 hour.
     * @return Reference to this for convenience.
     */
    EntityDataLoader transactionTimeout(int tt);

    /** If true instead of doing a query for each value from the file it will just try to insert it and if it fails then
     * it will try to update the existing record. Good for situations where most of the values will be new in the db.
     * @return Reference to this for convenience.
     */
    EntityDataLoader useTryInsert(boolean useTryInsert);

    /** If true will check all foreign key relationships for each value and if any of them are missing create a new
     * record with primary key only to avoid foreign key constraint errors.
     *
     * This should only be used when you are confident that the rest of the data for these new fk records will be loaded
     * from somewhere else to avoid having orphaned records.
     *
     * @return Reference to this for convenience.
     */
    EntityDataLoader dummyFks(boolean dummyFks);

    /** Set to true to disable Entity Facade ECA rules (for this import only, does not affect other things happening
     * in the system).
     * @return Reference to this for convenience.
     */
    EntityDataLoader disableEntityEca(boolean disableEeca);

    EntityDataLoader csvDelimiter(char delimiter);
    EntityDataLoader csvCommentStart(char commentStart);
    EntityDataLoader csvQuoteChar(char quoteChar);

    /** Only check the data against matching records in the database. Report on records that don't exist in the database
     * and any differences with records that have matching primary keys.
     *
     * @return List of messages about each comparison between data in the file(s) and data in the database.
     */
    List<String> check();

    /** Load the values into the database(s). */
    long load();

    /** Create an EntityList with all of the values from the data file(s).
     *
     * @return EntityList representing a List of EntityValue objects for the values in the XML document(s).
     */
    EntityList list();
}
