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
