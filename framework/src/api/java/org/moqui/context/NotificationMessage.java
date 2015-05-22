/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.context;

import java.util.Map;
import java.util.Set;

public interface NotificationMessage extends java.io.Serializable {

    NotificationMessage userId(String userId);
    NotificationMessage userIds(Set<String> userIds);
    Set<String> getUserIds();
    NotificationMessage userGroupId(String userGroupId);
    String getUserGroupId();

    NotificationMessage topic(String topic);
    String getTopic();

    /** Set the message as a JSON String. The top-level should be a Map (object).
     * @param messageJson The message as a JSON string containing a Map (object)
     * @return Self-reference for convenience
     */
    NotificationMessage message(String messageJson);
    NotificationMessage message(Map message);
    String getMessageJson();
    Map getMessageMap();

    /** Send this Notification Message.
     * @param persist If true this is persisted and message received is tracked. If false this is sent to active topic
     *                listeners only.
     * @return Self-reference for convenience
     */
    NotificationMessage send(boolean persist);

    String getNotificationMessageId();
    NotificationMessage markSent(String userId);
    NotificationMessage markReceived(String userId);
}
