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
package org.moqui.context;

import java.io.Writer;
import java.math.BigDecimal;
import java.util.List;

/**
 * Information about execution of an artifact as the system is running
 */
public interface ArtifactExecutionInfo {
    String getName();
    String getTypeEnumId();
    String getActionEnumId();

    String getAuthorizedUserId();
    String getAuthorizedAuthzTypeId();
    String getAuthorizedActionEnumId();
    boolean isAuthorizationInheritable();

    long getRunningTime();
    BigDecimal getRunningTimeMillis();
    long getThisRunningTime();
    BigDecimal getThisRunningTimeMillis();
    long getChildrenRunningTime();
    BigDecimal getChildrenRunningTimeMillis();
    List<ArtifactExecutionInfo> getChildList();
    ArtifactExecutionInfo getParent();
    BigDecimal getPercentOfParentTime();

    void print(Writer writer, int level, boolean children);
}
