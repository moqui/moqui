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
package org.moqui.impl.context.reference

import org.moqui.BaseException
import org.moqui.context.ExecutionContext
import org.moqui.context.ResourceReference
import org.moqui.impl.StupidUtilities
import org.moqui.impl.context.ResourceFacadeImpl

import javax.jcr.Property
import javax.jcr.Session
import org.moqui.entity.EntityValue
import org.moqui.entity.EntityList
import javax.sql.rowset.serial.SerialBlob

class DbResourceReference extends BaseResourceReference {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DbResourceReference.class)

    String location
    EntityValue dbResource = null
    EntityValue dbResourceFile = null

    DbResourceReference() { }
    
    @Override
    ResourceReference init(String location, ExecutionContext ec) {
        this.ec = ec
        this.location = location
        return this
    }

    ResourceReference init(String location, EntityValue dbResource, ExecutionContext ec) {
        this.ec = ec
        this.location = location
        this.dbResource = dbResource
        return this
    }

    @Override
    String getLocation() { location }

    String getPath() {
        if (!location) return ""
        // should have a prefix of "dbresource://"
        return location.substring("dbresource://".length())
    }

    @Override
    InputStream openStream() {
        EntityValue dbrf = getDbResourceFile()
        if (dbrf == null) return null
        return dbrf.getSerialBlob("fileData")?.getBinaryStream()
    }

    @Override
    String getText() { return StupidUtilities.getStreamText(openStream()) }

    @Override
    boolean supportsAll() { true }

    @Override
    boolean supportsUrl() { false }
    @Override
    URL getUrl() { return null }

    @Override
    boolean supportsDirectory() { true }
    @Override
    boolean isFile() { return getDbResource()?.isFile == "Y" }
    @Override
    boolean isDirectory() { return getDbResource() != null && getDbResource().isFile != "Y" }
    @Override
    List<ResourceReference> getDirectoryEntries() {
        List<ResourceReference> dirEntries = new LinkedList()
        EntityValue dbr = getDbResource()
        if (dbr == null) return dirEntries

        EntityList childList = ec.entity.makeFind("DbResource").condition([parentResourceId:dbr.resourceId])
                .useCache(true).list()
        for (EntityValue child in childList) {
            dirEntries.add(new DbResourceReference().init("${location}/${child.filename}", child, ec))
        }
        return dirEntries
    }

    @Override
    boolean supportsExists() { true }
    @Override
    boolean getExists() { return getDbResource() != null }

    boolean supportsLastModified() { true }
    long getLastModified() { return getDbResource()?.getTimestamp("lastUpdatedStamp")?.getTime() }

    boolean supportsWrite() { true }
    void putText(String text) {
        SerialBlob sblob = text ? new SerialBlob(text.getBytes()) : null
        EntityValue dbrf = getDbResourceFile()

        if (dbrf != null) {
            ec.service.sync().name("update", "DbResourceFile")
                    .parameters([resourceId:dbrf.resourceId, fileData:sblob]).call()
            dbResourceFile = null
        } else {
            // first make sure the directory exists that this is in
            List<String> filenameList = getPath().split("/")
            if (filenameList) filenameList.remove(filenameList.size()-1)
            EntityValue directoryValue = null
            String parentResourceId = null
            if (filenameList) {
                for (String filename in filenameList) {
                    directoryValue = ec.entity.makeFind("DbResource")
                            .condition([parentResourceId:parentResourceId, filename:filename])
                            .useCache(true).list().getFirst()
                    if (directoryValue == null) {
                        Map createResult = ec.service.sync().name("create", "DbResource")
                                .parameters([parentResourceId:parentResourceId, filename:filename, isFile:"N"]).call()
                        parentResourceId = createResult.resourceId
                        // logger.warn("=============== put text to ${location}, created dir ${filename}")
                    } else {
                        parentResourceId = directoryValue.resourceId
                        // logger.warn("=============== put text to ${location}, found existing dir ${filename}")
                    }
                }
            }

            // now write the DbResource and DbResourceFile records
            Map createDbrResult = ec.service.sync().name("create", "DbResource")
                    .parameters([parentResourceId:parentResourceId, filename:getFileName(), isFile:"Y"]).call()
            ec.service.sync().name("create", "DbResourceFile")
                    .parameters([resourceId:createDbrResult.resourceId, mimeType:getContentType(), fileData:sblob]).call()
            dbResourceFile = null
        }
    }
    void putStream(InputStream stream) {
        // TODO implement openOutputStream
        throw new BaseException("putStream for dbresource not yet supported")
    }
    void move(String newLocation) {
        // TODO implement move
        throw new BaseException("move for dbresource not yet supported")
    }

    EntityValue getDbResource() {
        if (dbResource != null) return dbResource

        List<String> filenameList = getPath().split("/")
        String parentResourceId = null
        EntityValue lastValue = null
        for (String filename in filenameList) {
            // NOTE: using .useCache(true).list().getFirst() because .useCache(true).one() tries to use the one cache
            // and that doesn't auto-clear correctly for non-pk queries
            lastValue = ec.entity.makeFind("DbResource").condition([parentResourceId:parentResourceId, filename:filename])
                    .useCache(true).list().getFirst()
            if (lastValue == null) continue
            parentResourceId = lastValue.resourceId
        }

        dbResource = lastValue
        return lastValue
    }
    EntityValue getDbResourceFile() {
        if (dbResourceFile != null) return dbResourceFile

        EntityValue dbr = getDbResource()
        if (dbr == null) return null

        // don't cache this, can be big and will be cached below this as text if needed
        EntityValue dbrf = ec.entity.makeFind("DbResourceFile").condition([resourceId:dbr.resourceId]).useCache(false).one()

        dbResourceFile = dbrf
        return dbrf
    }
}
