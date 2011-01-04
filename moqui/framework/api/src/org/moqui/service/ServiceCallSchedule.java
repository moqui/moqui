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
package org.moqui.service;

public interface ServiceCallSchedule extends ServiceCall {
    /** Name of the job. If specified repeated schedules with the same jobName will use the same underlying job.
     * @return Reference to this for convenience.
     */

    ServiceCallSchedule jobName(String jobName);
    /** Name of the service pool to send to (optional).
     * @return Reference to this for convenience.
     */
    ServiceCallSchedule poolName(String poolName);

    /** Time to first run this service (in milliseconds from Java epoch).
     * @return Reference to this for convenience.
     */
    ServiceCallSchedule startTime(long startTime);
    /** Number of times to repeat.
     * @return Reference to this for convenience.
     */
    ServiceCallSchedule count(int count);
    /** Time that this service schedule should expire (in milliseconds from Java epoch).
     * @return Reference to this for convenience.
     */
    ServiceCallSchedule endTime(long endTime);

    /** A time interval specifying how often to run this service.
     *
     * @param interval Number of units that make up the interval.
     * @param intervalUnit One of ServiceCall.IntervalUnit { SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS, YEARS }
     * @return Reference to this for convenience.
     */
    ServiceCallSchedule interval(int interval, IntervalUnit intervalUnit);
    /** A string in the same format used by cron to define a recurrence.
     * @return Reference to this for convenience.
     */
    ServiceCallSchedule cron(String cronString);

    /** Maximum number of times to retry running this service.
     * @return Reference to this for convenience.
     */
    ServiceCallSchedule maxRetry(int maxRetry);

    /** Schedule the service call. */
    void call() throws ServiceException;
}
