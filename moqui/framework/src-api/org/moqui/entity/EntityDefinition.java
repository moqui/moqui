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

public interface EntityDefinition {
    // NOTE: this interface is pretty empty because most of it will be generated from the XSD using XMLBeans

    /** Returns the full table name including the prefix from the datasource config and the table-name or converted entity-name */
    String getFullTableName();

    boolean isField(String fieldName);

    boolean areFields(Set<String> fieldNames);

    public List<String> getFieldNames();

    public List<String> getPkFieldNames();

    public List<String> getNonPkFieldNames();

    public List<EntityFieldDefinition> getFields();

    public EntityFieldDefinition getField(String fieldName);
}
