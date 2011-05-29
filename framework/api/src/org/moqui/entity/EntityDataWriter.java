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
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** Used to write XML entity data from the database to a writer.
 *
 * The document will have a root element like <code>&lt;entity-facade-xml&gt;</code>.
 */
public interface EntityDataWriter {
    /** A List of entity names to write.
     * @return Reference to this for convenience.
     */
    EntityDataWriter entityNames(List<String> entityNames);
    /** Should the dependent records of each record be written?
     * @return Reference to this for convenience.
     */
    EntityDataWriter dependentRecords(boolean dependents);
    /** Field name, value pairs to filter the results by. Each name/value only used on entities with a field matching
     * the name.
     * @return Reference to this for convenience.
     */
    EntityDataWriter filterMap(Map<String, Object> filterMap);

    /** From date for lastUpdatedStamp on each entity.
     * @return Reference to this for convenience.
     */
    EntityDataWriter fromDate(Timestamp fromDate);
    /** Thru date for lastUpdatedStamp on each entity.
     * @return Reference to this for convenience.
     */
    EntityDataWriter thruDate(Timestamp thruDate);

    void file(String filename);
    void directory(String path);
    void writer(Writer writer);
}
