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

import java.util.Map;

/**
 * Represents the conditions to be used to constrain a query.
 *
 * These can be used in various combinations using the different condition types.
 *
 */
public interface EntityCondition {
    public enum ComparisonOperator { EQUALS, NOT_EQUAL,
        LESS_THAN, GREATER_THAN, LESS_THAN_EQUAL_TO, GREATER_THAN_EQUAL_TO,
        IN, NOT_IN, BETWEEN, LIKE, NOT_LIKE }

    public enum JoinOperator { AND, OR }

    // TODO: any external methods needed here, or all internal?
}
