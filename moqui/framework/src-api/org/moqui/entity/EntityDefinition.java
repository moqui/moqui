/*
 * Copyright 2010 David E. Jones
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
