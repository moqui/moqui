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
package org.moqui.impl.entity

import org.moqui.entity.EntityListIterator
import org.moqui.entity.EntityValue
import org.moqui.entity.EntityList
import java.sql.ResultSet

class EntityListIteratorImpl implements EntityListIterator {

    protected EntityFacadeImpl efi
    protected ResultSet rs

    EntityListIteratorImpl(ResultSet rs, EntityFacadeImpl efi) {
        this.efi = efi
        this.rs = rs
    }

    @Override
    void afterLast() {
        // TODO implement this
    }

    @Override
    void beforeFirst() {
        // TODO implement this
    }

    @Override
    boolean last() {
        // TODO implement this
        return false
    }

    @Override
    boolean first() {
        // TODO implement this
        return false
    }

    @Override
    void close() {
        // TODO implement this
    }

    @Override
    EntityValue currentEntityValue() {
        // TODO implement this
        return null
    }

    @Override
    int currentIndex() {
        // TODO implement this
        return 0
    }

    @Override
    boolean absolute(int rowNum) {
        // TODO implement this
        return false
    }

    @Override
    boolean relative(int rows) {
        // TODO implement this
        return false
    }

    @Override
    boolean hasNext() {
        // TODO implement this
        return false
    }

    @Override
    boolean hasPrevious() {
        // TODO implement this
        return false
    }

    @Override
    EntityValue next() {
        // TODO implement this
        return null
    }

    @Override
    int nextIndex() {
        // TODO implement this
        return 0
    }

    @Override
    EntityValue previous() {
        // TODO implement this
        return null
    }

    @Override
    int previousIndex() {
        // TODO implement this
        return 0
    }

    @Override
    void setFetchSize(int rows) {
        // TODO implement this
    }

    @Override
    EntityList getCompleteList() {
        // TODO implement this
        return null
    }

    @Override
    EntityList getPartialList(int start, int number) {
        // TODO implement this
        return null
    }

    @Override
    int getResultsSizeAfterPartialList() {
        // TODO implement this
        return 0
    }

    @Override
    Iterator<EntityValue> iterator() {
        // TODO implement this
        return null
    }

    @Override
    void remove() {
        // TODO implement this
    }

    @Override
    void set(EntityValue e) {
        // TODO implement this
    }

    @Override
    void add(EntityValue e) {
        // TODO implement this
    }
}
