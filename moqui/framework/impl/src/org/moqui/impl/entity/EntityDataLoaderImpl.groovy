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

import javax.transaction.Transaction
import javax.xml.parsers.SAXParserFactory

import org.apache.commons.codec.binary.Base64

import org.moqui.context.TransactionException
import org.moqui.context.TransactionFacade
import org.moqui.context.ResourceReference
import org.moqui.entity.EntityException
import org.moqui.entity.EntityDataLoader
import org.moqui.entity.EntityList
import org.moqui.entity.EntityValue

import org.slf4j.LoggerFactory
import org.slf4j.Logger

import org.xml.sax.helpers.DefaultHandler
import org.xml.sax.Attributes
import org.xml.sax.XMLReader
import org.xml.sax.InputSource
import org.xml.sax.Locator
import org.xml.sax.SAXException
import org.apache.commons.collections.set.ListOrderedSet

class EntityDataLoaderImpl implements EntityDataLoader {
    protected final static Logger logger = LoggerFactory.getLogger(EntityFacadeImpl.class)

    protected EntityFacadeImpl efi

    // NOTE: these are Groovy Beans style with no access modifier, results in private fields with implicit getters/setters
    List<String> locationList = new LinkedList<String>()
    String xmlText = null
    Set<String> dataTypes = new HashSet<String>()

    int transactionTimeout = 600
    boolean useTryInsert = false
    boolean dummyFks = false

    EntityDataLoaderImpl(EntityFacadeImpl efi) {
        this.efi = efi
    }

    EntityFacadeImpl getEfi() { return efi }

    EntityDataLoader location(String location) { this.locationList.add(location); return this }
    EntityDataLoader locationList(List<String> ll) { this.locationList.addAll(ll); return this }
    EntityDataLoader xmlText(String xmlText) { this.xmlText = xmlText; return this }
    EntityDataLoader dataTypes(Set<String> dataTypes) { this.dataTypes.addAll(dataTypes); return this }

    EntityDataLoader transactionTimeout(int tt) { this.transactionTimeout = tt; return this }
    EntityDataLoader useTryInsert(boolean useTryInsert) { this.useTryInsert = useTryInsert; return this }
    EntityDataLoader dummyFks(boolean dummyFks) { this.dummyFks = dummyFks; return this }

    List<String> check() {
        CheckValueHandler cvh = new CheckValueHandler(this)
        EntityXmlHandler exh = new EntityXmlHandler(this, cvh)

        internalRun(exh)
        return cvh.getMessageList()
    }

    long load() {
        LoadValueHandler lvh = new LoadValueHandler(this)
        EntityXmlHandler exh = new EntityXmlHandler(this, lvh)

        internalRun(exh)
        return exh.getValuesRead()
    }

    EntityList list() {
        ListValueHandler lvh = new ListValueHandler(this)
        EntityXmlHandler exh = new EntityXmlHandler(this, lvh)

        internalRun(exh)
        return lvh.entityList
    }

    void internalRun(EntityXmlHandler exh) {
        // if no xmlText or locations, so find all of the component and entity-facade files
        if (!this.xmlText && !this.locationList) {
            // loop through all of the entity-facade.load-entity nodes, check each for "<entities>" root element
            for (Node loadData in efi.ecfi.getConfXmlRoot()."entity-facade"[0]."load-data") {
                locationList.add(loadData."@location")
            }

            for (String location in efi.ecfi.getComponentBaseLocations().values()) {
                ResourceReference dataDirRr = efi.ecfi.resourceFacade.getLocationReference(location + "/data")
                if (dataDirRr.supportsAll()) {
                    // if directory doesn't exist skip it, component doesn't have a data directory
                    if (!dataDirRr.exists || !dataDirRr.isDirectory()) continue
                    // get all files in the directory
                    for (ResourceReference dataRr in dataDirRr.directoryEntries) {
                        if (!dataRr.isFile() || !dataRr.location.endsWith(".xml")) continue
                        locationList.add(dataRr.location)
                    }
                } else {
                    // just warn here, no exception because any non-file component location would blow everything up
                    logger.warn("Cannot load entity data file in component location [${location}] because protocol [${dataDirRr.uri.scheme}] is not yet supported.")
                }
            }
        }
        if (locationList && logger.infoEnabled) {
            logger.info("Loading entity XML data from the following locations: " + locationList)
        }

        TransactionFacade tf = efi.ecfi.transactionFacade
        Transaction parentTransaction = null
        try {
            if (tf.isTransactionInPlace()) parentTransaction = tf.suspend()
            // load the XML text in its own transaction
            boolean beganTransaction = tf.begin(transactionTimeout)
            try {
                if (this.xmlText) {
                    XMLReader reader = SAXParserFactory.newInstance().newSAXParser().XMLReader
                    reader.setContentHandler(exh)
                    reader.parse(new InputSource(new StringReader(this.xmlText)))
                }
            } catch (Throwable t) {
                tf.rollback(beganTransaction, "Error loading XML text", t)
            } finally {
                if (tf.isTransactionInPlace()) tf.commit(beganTransaction)
            }
            // load each file in its own transaction
            for (String location in this.locationList) {
                loadSingleFile(location, exh)
            }
        } catch (TransactionException e) {
            throw e
        } finally {
            if (parentTransaction != null) tf.resume(parentTransaction)
        }
    }

    void loadSingleFile(String location, EntityXmlHandler exh) {
        TransactionFacade tf = efi.ecfi.transactionFacade
        boolean beganTransaction = tf.begin(transactionTimeout)
        try {
            InputStream inputStream = null
            try {
                logger.info("Loading entity XML data from [${location}]")
                long beforeRecords = exh.valuesRead ?: 0
                long beforeTime = System.currentTimeMillis()

                inputStream = efi.ecfi.resourceFacade.getLocationStream(location)
                XMLReader reader = SAXParserFactory.newInstance().newSAXParser().XMLReader
                reader.setContentHandler(exh)
                reader.parse(new InputSource(inputStream))

                logger.info("Loaded ${(exh.valuesRead?:0) - beforeRecords} records from [${location}] in ${((System.currentTimeMillis() - beforeTime)/1000)} seconds")
            } catch (TypeToSkipException e) {
                // nothing to do, this just stops the parsing when we know the file is not in the types we want
            } finally {
                if (inputStream) inputStream.close()
            }
        } catch (Throwable t) {
            tf.rollback(beganTransaction, "Error loading XML text", t)
            throw new IllegalArgumentException("Error loading XML data file [${location}]", t)
        } finally {
            if (tf.isTransactionInPlace()) tf.commit(beganTransaction)
        }
    }

    static abstract class ValueHandler {
        protected EntityDataLoaderImpl edli
        ValueHandler(EntityDataLoaderImpl edli) { this.edli = edli }
        abstract void handleValue(EntityValue value)
    }
    static class CheckValueHandler extends ValueHandler {
        protected List<String> messageList = new LinkedList()
        CheckValueHandler(EntityDataLoaderImpl edli) { super(edli) }
        List<String> getMessageList() { return messageList }
        void handleValue(EntityValue value) {
            value.checkAgainstDatabase(messageList)
        }
    }
    static class LoadValueHandler extends ValueHandler {
        LoadValueHandler(EntityDataLoaderImpl edli) { super(edli) }
        void handleValue(EntityValue value) {
            if (edli.dummyFks) value.checkFks(true)
            if (edli.useTryInsert) {
                try {
                    value.create()
                } catch (EntityException e) {
                    // if this fails we have a real error so let the exception fall through
                    value.update()
                }
            } else {
                value.createOrUpdate()
            }
        }
    }
    static class ListValueHandler extends ValueHandler {
        protected EntityList el
        ListValueHandler(EntityDataLoaderImpl edli) { super(edli); el = new EntityListImpl(edli.efi) }
        void handleValue(EntityValue value) {
            el.add(value)
        }
        EntityList getEntityList() { return el }
    }

    static class TypeToSkipException extends RuntimeException {
        TypeToSkipException() { }
    }

    static class EntityXmlHandler extends DefaultHandler {
        protected Locator locator
        protected EntityDataLoaderImpl edli
        protected ValueHandler valueHandler

        protected EntityValueImpl currentValue = null
        protected String currentFieldName = null
        protected StringBuilder currentFieldValue = null
        protected long valuesRead = 0
        protected List<String> messageList = new LinkedList()

        EntityXmlHandler(EntityDataLoaderImpl edli, ValueHandler valueHandler) {
            this.edli = edli
            this.valueHandler = valueHandler
        }

        ValueHandler getValueHandler() { return valueHandler }
        long getValuesRead() { return valuesRead }
        List<String> getMessageList() { return messageList }

        void startElement(String ns, String localName, String qName, Attributes attributes) {
            // logger.info("startElement ns [${ns}], localName [${localName}] qName [${qName}]")
            if (qName == "entity-facade-xml") {
                String type = attributes.getValue("type")
                if (type && edli.dataTypes && !edli.dataTypes.contains(type)) throw new TypeToSkipException()
                return
            }

            if (currentValue != null) {
                // nested value/CDATA element
                currentFieldName = qName
            } else {
                String entityName = qName

                // if a dash or colon is in the tag name, grab what is after it
                if (entityName.contains('-')) entityName = entityName.substring(entityName.indexOf('-') + 1)
                if (entityName.contains(':')) entityName = entityName.substring(entityName.indexOf(':') + 1)

                if (edli.efi.getEntityDefinition(entityName)) {
                    currentValue = edli.efi.makeValue(entityName)

                    int length = attributes.getLength()
                    for (int i = 0; i < length; i++) {
                        String name = attributes.getLocalName(i)
                        String value = attributes.getValue(i)

                        if (!name) name = attributes.getQName(i)
                        try {
                            // treat empty strings as nulls
                            if (value) {
                                if (currentValue.getEntityDefinition().isField(name)) {
                                    currentValue.setString(name, value)
                                } else {
                                    logger.warn("Ignoring invalid attribute name [${name}] for entity [${currentValue.getEntityName()}] with value [${value}] because it is not field of that entity")
                                }
                            }
                        } catch (Exception e) {
                            logger.warn("Could not set field [${entityName}.${name}] to the value [${value}]", e)
                        }
                    }
                } else {
                    logger.warn("Found element name [${entityName}] that is not a valid entity name")
                }
            }
        }
        void characters(char[] chars, int offset, int length) {
            if (currentValue != null && currentFieldName) {
                if (currentFieldValue == null) currentFieldValue = new StringBuilder()
                currentFieldValue.append(chars, offset, length)
            }
        }
        void endElement(String ns, String localName, String qName) {
            if (qName == "entity-facade-xml") return
            if (currentValue != null) {
                if (currentFieldName != null) {
                    if (currentFieldValue) {
                        EntityDefinition ed = currentValue.getEntityDefinition()
                        if (ed.isField(currentFieldName)) {
                            Node fieldNode = ed.getFieldNode(currentFieldName)
                            String type = fieldNode."@type"
                            if (type == "binary-very-long") {
                                byte[] binData = Base64.decodeBase64(currentFieldValue.toString())
                                currentValue.set(currentFieldName, binData)
                            } else {
                                currentValue.setString(currentFieldName, currentFieldValue.toString())
                            }
                        } else {
                            logger.warn("Ignoring invalid field name [${currentFieldName}] found for the entity ${currentValue.getEntityName()} with value ${currentFieldValue}")
                        }
                        currentFieldValue = null
                    }
                    currentFieldName = null
                } else {
                    // before we write currentValue check to see if PK is there, if not and it is one field, generate it from a sequence using the entity name
                    if (!currentValue.containsPrimaryKey()) {
                        ListOrderedSet pkFieldList = currentValue.getEntityDefinition().getFieldNames(true, false)
                        if (pkFieldList.size() == 1) {
                            String newSeq = edli.efi.sequencedIdPrimary(currentValue.getEntityName(), null)
                            currentValue.setString((String) pkFieldList.get(0), newSeq)
                        } else {
                            throw new SAXException("Cannot store value with incomplete primary key with more than 1 primary key field: " + currentValue)
                        }
                    }

                    try {
                        valueHandler.handleValue(currentValue)
                        valuesRead++
                        currentValue = null
                    } catch (EntityException e) {
                        logger.error("Error storing value", e)
                        throw new SAXException("Error storing value: " + e.toString())
                    }
                }
            }
        }

        public void setDocumentLocator(org.xml.sax.Locator locator) {
            this.locator = locator;
        }
    }
}
