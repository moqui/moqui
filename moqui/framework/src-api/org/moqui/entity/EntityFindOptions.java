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

/**
 * Contains a number of variables used to select certain advanced finding options.
 */
public class EntityFindOptions implements java.io.Serializable {

    protected int resultSetType = java.sql.ResultSet.TYPE_FORWARD_ONLY;
    protected int resultSetConcurrency = java.sql.ResultSet.CONCUR_READ_ONLY;
    protected Integer fetchSize = null;
    protected Integer maxRows = null;
    protected boolean distinct = false;

    /** Default constructor. Defaults are as follows:
     *      resultSetType = TYPE_FORWARD_ONLY
     *      resultSetConcurrency = CONCUR_READ_ONLY
     *      fetchSize = (all rows)
     *      maxRows = (all rows)
     *      distinct = false
     */
    public EntityFindOptions() {}

    public EntityFindOptions(Integer resultSetType, Integer resultSetConcurrency, Integer fetchSize, Integer maxRows, Boolean distinct) {
        if (resultSetType != null) this.resultSetType = resultSetType;
        if (resultSetConcurrency != null) this.resultSetConcurrency = resultSetConcurrency;
        this.fetchSize = fetchSize;
        this.maxRows = maxRows;
        if (distinct != null) this.distinct = distinct;
    }

    public int getResultSetType() {
        return resultSetType;
    }

    /** Specifies how the ResultSet will be traversed. Available values: ResultSet.TYPE_FORWARD_ONLY,
     *      ResultSet.TYPE_SCROLL_INSENSITIVE or ResultSet.TYPE_SCROLL_SENSITIVE. See the java.sql.ResultSet JavaDoc for
     *      more information. If you want it to be fast, use the common option: ResultSet.TYPE_FORWARD_ONLY.
     *      For partial results where you want to jump to an index make sure to use TYPE_SCROLL_INSENSITIVE.
     */
    public void setResultSetType(Integer resultSetType) {
        this.resultSetType = resultSetType;
    }

    public int getResultSetConcurrency() {
        return resultSetConcurrency;
    }

    /** Specifies whether or not the ResultSet can be updated. Available values:
     *      ResultSet.CONCUR_READ_ONLY or ResultSet.CONCUR_UPDATABLE. Should pretty much always be
     *      ResultSet.CONCUR_READ_ONLY with the Entity Facade since updates are generally done as separate operations.
     */
    public void setResultSetConcurrency(int resultSetConcurrency) {
        this.resultSetConcurrency = resultSetConcurrency;
    }

    public int getFetchSize() {
        return fetchSize != null ? fetchSize : 0;
    }

    /** Specifies the fetch size for this query. null will fall back to datasource settings. */
    public void setFetchSize(Integer fetchSize) {
        this.fetchSize = fetchSize;
    }

    public int getMaxRows() {
        return maxRows != null ? maxRows : 0;
    }

    /** Specifies the max number of rows to return, null means all rows. */
    public void setMaxRows(Integer maxRows) {
        this.maxRows = maxRows;
    }

    public boolean getDistinct() {
        return distinct;
    }

    /** Specifies whether the values returned should be filtered to remove duplicate values. */
    public void setDistinct(boolean distinct) {
        this.distinct = distinct;
    }
}
