/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
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
