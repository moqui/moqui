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

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.moqui.BaseException
import org.moqui.impl.service.ServiceCallSyncImpl
import org.moqui.impl.service.ServiceFacadeImpl
import org.moqui.service.ServiceCallSync

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

class EntityDataLoaderImpl implements EntityDataLoader {
    protected final static Logger logger = LoggerFactory.getLogger(EntityDataLoaderImpl.class)

    protected EntityFacadeImpl efi
    protected ServiceFacadeImpl sfi

    // NOTE: these are Groovy Beans style with no access modifier, results in private fields with implicit getters/setters
    List<String> locationList = new LinkedList<String>()
    String xmlText = null
    String csvText = null
    Set<String> dataTypes = new HashSet<String>()

    int transactionTimeout = 600
    boolean useTryInsert = false
    boolean dummyFks = false
    boolean disableEeca = false

    char csvDelimiter = ','
    char csvCommentStart = '#'
    char csvQuoteChar = '"'

    EntityDataLoaderImpl(EntityFacadeImpl efi) {
        this.efi = efi
        this.sfi = efi.getEcfi().getServiceFacade()
    }

    EntityFacadeImpl getEfi() { return efi }

    @Override
    EntityDataLoader location(String location) { this.locationList.add(location); return this }
    @Override
    EntityDataLoader locationList(List<String> ll) { this.locationList.addAll(ll); return this }
    @Override
    EntityDataLoader xmlText(String xmlText) { this.xmlText = xmlText; return this }
    @Override
    EntityDataLoader csvText(String csvText) { this.csvText = csvText; return this }
    @Override
    EntityDataLoader dataTypes(Set<String> dataTypes) {
        for (String dt in dataTypes) this.dataTypes.add(dt.trim())
        return this
    }

    @Override
    EntityDataLoader transactionTimeout(int tt) { this.transactionTimeout = tt; return this }
    @Override
    EntityDataLoader useTryInsert(boolean useTryInsert) { this.useTryInsert = useTryInsert; return this }
    @Override
    EntityDataLoader dummyFks(boolean dummyFks) { this.dummyFks = dummyFks; return this }
    @Override
    EntityDataLoader disableEntityEca(boolean disableEeca) { this.disableEeca = disableEeca; return this }

    @Override
    EntityDataLoader csvDelimiter(char delimiter) { this.csvDelimiter = delimiter; return this }
    @Override
    EntityDataLoader csvCommentStart(char commentStart) { this.csvCommentStart = commentStart; return this }
    @Override
    EntityDataLoader csvQuoteChar(char quoteChar) { this.csvQuoteChar = quoteChar; return this }

    List<String> check() {
        CheckValueHandler cvh = new CheckValueHandler(this)
        EntityXmlHandler exh = new EntityXmlHandler(this, cvh)
        EntityCsvHandler ech = new EntityCsvHandler(this, cvh)

        internalRun(exh, ech)
        return cvh.getMessageList()
    }

    long load() {
        LoadValueHandler lvh = new LoadValueHandler(this)
        EntityXmlHandler exh = new EntityXmlHandler(this, lvh)
        EntityCsvHandler ech = new EntityCsvHandler(this, lvh)

        internalRun(exh, ech)
        return exh.getValuesRead() + ech.getValuesRead()
    }

    EntityList list() {
        ListValueHandler lvh = new ListValueHandler(this)
        EntityXmlHandler exh = new EntityXmlHandler(this, lvh)
        EntityCsvHandler ech = new EntityCsvHandler(this, lvh)

        internalRun(exh, ech)
        return lvh.entityList
    }

    void internalRun(EntityXmlHandler exh, EntityCsvHandler ech) {
        boolean reenableEeca = false
        if (this.disableEeca) reenableEeca = !this.efi.ecfi.eci.artifactExecution.disableEntityEca()

        // if no xmlText or locations, so find all of the component and entity-facade files
        if (!this.xmlText && !this.locationList) {
            // if we're loading seed type data, add entity def files to the list of locations to load
            if (!dataTypes || dataTypes.contains("seed")) {
                for (ResourceReference entityRr in efi.getAllEntityFileLocations())
                    if (!entityRr.location.endsWith(".eecas.xml")) locationList.add(entityRr.location)
            }

            // loop through all of the entity-facade.load-entity nodes, check each for "<entities>" root element
            for (Node loadData in efi.ecfi.getConfXmlRoot()."entity-facade"[0]."load-data") {
                locationList.add((String) loadData."@location")
            }

            for (String location in efi.ecfi.getComponentBaseLocations().values()) {
                ResourceReference dataDirRr = efi.ecfi.resourceFacade.getLocationReference(location + "/data")
                if (dataDirRr.supportsAll()) {
                    // if directory doesn't exist skip it, component doesn't have a data directory
                    if (!dataDirRr.exists || !dataDirRr.isDirectory()) continue
                    // get all files in the directory
                    TreeMap<String, ResourceReference> dataDirEntries = new TreeMap<String, ResourceReference>()
                    for (ResourceReference dataRr in dataDirRr.directoryEntries) {
                        if (!dataRr.isFile() || (!dataRr.location.endsWith(".xml") && !dataRr.location.endsWith(".csv"))) continue
                        dataDirEntries.put(dataRr.getFileName(), dataRr)
                    }
                    for (Map.Entry<String, ResourceReference> dataDirEntry in dataDirEntries) {
                        locationList.add(dataDirEntry.getValue().location)
                    }
                } else {
                    // just warn here, no exception because any non-file component location would blow everything up
                    logger.warn("Cannot load entity data file in component location [${location}] because protocol [${dataDirRr.uri.scheme}] is not yet supported.")
                }
            }
        }
        if (locationList && logger.isInfoEnabled()) {
            StringBuilder lm = new StringBuilder("Loading entity data from the following locations: ")
            for (String loc in locationList) lm.append("\n - ").append(loc)
            logger.info(lm.toString())
            logger.info("Loading data types: ${dataTypes ?: 'ALL'}")
        }

        TransactionFacade tf = efi.ecfi.transactionFacade
        boolean suspendedTransaction = false
        try {
            if (tf.isTransactionInPlace()) suspendedTransaction = tf.suspend()
            // load the XML text in its own transaction
            if (this.xmlText) {
                boolean beganTransaction = tf.begin(transactionTimeout)
                try {
                    XMLReader reader = SAXParserFactory.newInstance().newSAXParser().XMLReader
                    exh.setLocation("xmlText")
                    reader.setContentHandler(exh)
                    reader.parse(new InputSource(new StringReader(this.xmlText)))
                } catch (Throwable t) {
                    tf.rollback(beganTransaction, "Error loading XML entity data", t)
                } finally {
                    if (beganTransaction && tf.isTransactionInPlace()) tf.commit()
                }
            }

            // load the CSV text in its own transaction
            if (this.csvText) {
                boolean beganTransaction = tf.begin(transactionTimeout)
                InputStream csvInputStream = new ByteArrayInputStream(csvText.getBytes("UTF-8"))
                try {
                    ech.loadFile("csvText", csvInputStream)
                } catch (Throwable t) {
                    tf.rollback(beganTransaction, "Error loading CSV entity data", t)
                } finally {
                    if (csvInputStream != null) csvInputStream.close()
                    if (beganTransaction && tf.isTransactionInPlace()) tf.commit()
                }
            }

            // load each file in its own transaction
            for (String location in this.locationList) {
                loadSingleFile(location, exh, ech)
            }
        } catch (TransactionException e) {
            throw e
        } finally {
            if (suspendedTransaction) tf.resume()
        }

        if (reenableEeca) this.efi.ecfi.eci.artifactExecution.enableEntityEca()
    }

    void loadSingleFile(String location, EntityXmlHandler exh, EntityCsvHandler ech) {
        TransactionFacade tf = efi.ecfi.transactionFacade
        boolean beganTransaction = tf.begin(transactionTimeout)
        try {
            InputStream inputStream = null
            try {
                logger.info("Loading entity data from [${location}]")
                long beforeTime = System.currentTimeMillis()

                inputStream = efi.ecfi.resourceFacade.getLocationStream(location)

                if (location.endsWith(".xml")) {
                    long beforeRecords = exh.valuesRead ?: 0
                    XMLReader reader = SAXParserFactory.newInstance().newSAXParser().XMLReader
                    exh.setLocation(location)
                    reader.setContentHandler(exh)
                    reader.parse(new InputSource(inputStream))
                    logger.info("Loaded ${(exh.valuesRead?:0) - beforeRecords} records from [${location}] in ${((System.currentTimeMillis() - beforeTime)/1000)} seconds")
                } else if (location.endsWith(".csv")) {
                    long beforeRecords = ech.valuesRead ?: 0
                    if (ech.loadFile(location, inputStream)) {
                        logger.info("Loaded ${(ech.valuesRead?:0) - beforeRecords} records from [${location}] in ${((System.currentTimeMillis() - beforeTime)/1000)} seconds")
                    }
                }

            } catch (TypeToSkipException e) {
                // nothing to do, this just stops the parsing when we know the file is not in the types we want
            } finally {
                if (inputStream != null) inputStream.close()
            }
        } catch (Throwable t) {
            tf.rollback(beganTransaction, "Error loading entity data", t)
            throw new IllegalArgumentException("Error loading entity data file [${location}]", t)
        } finally {
            if (beganTransaction && tf.isTransactionInPlace()) tf.commit()
        }
    }

    static abstract class ValueHandler {
        protected EntityDataLoaderImpl edli
        ValueHandler(EntityDataLoaderImpl edli) { this.edli = edli }
        abstract void handleValue(EntityValue value)
        abstract void handleService(ServiceCallSync scs)
    }
    static class CheckValueHandler extends ValueHandler {
        protected List<String> messageList = new LinkedList()
        CheckValueHandler(EntityDataLoaderImpl edli) { super(edli) }
        List<String> getMessageList() { return messageList }
        void handleValue(EntityValue value) { value.checkAgainstDatabase(messageList) }
        void handleService(ServiceCallSync scs) { messageList.add("Not calling service [${scs.getServiceName()}] with parameters ${scs.getCurrentParameters()}") }
    }
    static class LoadValueHandler extends ValueHandler {
        LoadValueHandler(EntityDataLoaderImpl edli) { super(edli) }
        void handleValue(EntityValue value) {
            if (edli.dummyFks) value.checkFks(true)
            if (edli.useTryInsert) {
                try {
                    value.create()
                } catch (EntityException e) {
                    if (logger.isTraceEnabled()) logger.trace("Insert failed, trying update (${e.toString()})")
                    // retry, then if this fails we have a real error so let the exception fall through
                    value.update()
                }
            } else {
                value.createOrUpdate()
            }
        }
        void handleService(ServiceCallSync scs) {
            Map results = scs.call()
            logger.info("Called service [${scs.getServiceName()}] in data load, results: ${results}")
        }
    }
    static class ListValueHandler extends ValueHandler {
        protected EntityList el
        ListValueHandler(EntityDataLoaderImpl edli) { super(edli); el = new EntityListImpl(edli.efi) }
        void handleValue(EntityValue value) {
            el.add(value)
        }
        EntityList getEntityList() { return el }
        void handleService(ServiceCallSync scs) { logger.warn("For load to EntityList not calling service [${scs.getServiceName()}] with parameters ${scs.getCurrentParameters()}") }
    }

    static class TypeToSkipException extends RuntimeException {
        TypeToSkipException() { }
    }

    static class EntityXmlHandler extends DefaultHandler {
        protected Locator locator
        protected EntityDataLoaderImpl edli
        protected ValueHandler valueHandler

        protected EntityValueImpl currentEntityValue = null
        protected ServiceCallSyncImpl currentScs = null
        protected String currentFieldName = null
        protected StringBuilder currentFieldValue = null
        protected long valuesRead = 0
        protected List<String> messageList = new LinkedList()
        String location

        protected boolean loadElements = false

        EntityXmlHandler(EntityDataLoaderImpl edli, ValueHandler valueHandler) {
            this.edli = edli
            this.valueHandler = valueHandler
        }

        ValueHandler getValueHandler() { return valueHandler }
        long getValuesRead() { return valuesRead }
        List<String> getMessageList() { return messageList }

        void startElement(String ns, String localName, String qName, Attributes attributes) {
            // logger.info("startElement ns [${ns}], localName [${localName}] qName [${qName}]")
            String type = null
            if (qName == "entity-facade-xml") { type = attributes.getValue("type") }
            else if (qName == "seed-data") { type = "seed" }
            if (type && edli.dataTypes && !edli.dataTypes.contains(type)) {
                if (logger.isInfoEnabled()) logger.info("Skipping file [${location}], is a type to skip (${type})")
                throw new TypeToSkipException()
            }

            if (qName == "entity-facade-xml") {
                loadElements = true
                return
            } else if (qName == "seed-data") {
                loadElements = true
                return
            }
            if (!loadElements) return

            if (currentEntityValue != null || currentScs != null) {
                // nested value/CDATA element
                currentFieldName = qName
            } else {
                String entityName = qName
                // get everything after a colon, but replace - with # for verb#noun separation
                if (entityName.contains(':')) entityName = entityName.substring(entityName.indexOf(':') + 1)
                if (entityName.contains('-')) entityName = entityName.replace('-', '#')

                if (edli.efi.isEntityDefined(entityName)) {
                    currentEntityValue = (EntityValueImpl) edli.efi.makeValue(entityName)

                    int length = attributes.getLength()
                    for (int i = 0; i < length; i++) {
                        String name = attributes.getLocalName(i)
                        String value = attributes.getValue(i)
                        if (!name) name = attributes.getQName(i)

                        try {
                            // treat empty strings as nulls
                            if (value) {
                                if (currentEntityValue.getEntityDefinition().isField(name)) {
                                    currentEntityValue.setString(name, value)
                                } else {
                                    logger.warn("Ignoring invalid attribute name [${name}] for entity [${currentEntityValue.getEntityName()}] with value [${value}] because it is not field of that entity")
                                }
                            } else {
                                currentEntityValue.set(name, null)
                            }
                        } catch (Exception e) {
                            logger.warn("Could not set field [${entityName}.${name}] to the value [${value}]", e)
                        }
                    }
                } else if (edli.sfi.isServiceDefined(entityName)) {
                    currentScs = (ServiceCallSyncImpl) edli.sfi.sync().name(entityName)
                    int length = attributes.getLength()
                    for (int i = 0; i < length; i++) {
                        String name = attributes.getLocalName(i)
                        String value = attributes.getValue(i)
                        if (!name) name = attributes.getQName(i)

                        // treat empty strings as nulls
                        if (value) {
                            currentScs.parameter(name, value)
                        } else {
                            currentScs.parameter(name, null)
                        }
                    }
                } else {
                    throw new SAXException("Found element [${qName}] name, transformed to [${entityName}], that is not a valid entity name or service name")
                }
            }
        }
        void characters(char[] chars, int offset, int length) {
            if ((currentEntityValue != null || currentScs != null) && currentFieldName) {
                if (currentFieldValue == null) currentFieldValue = new StringBuilder()
                currentFieldValue.append(chars, offset, length)
            }
        }
        void endElement(String ns, String localName, String qName) {
            if (qName == "entity-facade-xml" || qName == "seed-data") {
                loadElements = false
                return
            }
            if (!loadElements) return

            if (currentFieldName != null) {
                if (currentFieldValue) {
                    if (currentEntityValue != null) {
                        EntityDefinition ed = currentEntityValue.getEntityDefinition()
                        if (ed.isField(currentFieldName)) {
                            Node fieldNode = ed.getFieldNode(currentFieldName)
                            String type = fieldNode."@type"
                            if (type == "binary-very-long") {
                                byte[] binData = Base64.decodeBase64(currentFieldValue.toString())
                                currentEntityValue.setBytes(currentFieldName, binData)
                            } else {
                                currentEntityValue.setString(currentFieldName, currentFieldValue.toString())
                            }
                        } else {
                            logger.warn("Ignoring invalid field name [${currentFieldName}] found for the entity ${currentEntityValue.getEntityName()} with value ${currentFieldValue}")
                        }
                    } else if (currentScs != null) {
                        currentScs.parameter(currentFieldName, currentFieldValue)
                    }
                    currentFieldValue = null
                }
                currentFieldName = null
            } else {
                if (currentEntityValue != null) {
                    // before we write currentValue check to see if PK is there, if not and it is one field, generate it from a sequence using the entity name
                    if (!currentEntityValue.containsPrimaryKey()) {
                        if (currentEntityValue.getEntityDefinition().getPkFieldNames().size() == 1) {
                            currentEntityValue.setSequencedIdPrimary()
                        } else {
                            throw new SAXException("Cannot process value with incomplete primary key for [${currentEntityValue.getEntityName()}] with more than 1 primary key field: " + currentEntityValue)
                        }
                    }

                    try {
                        valueHandler.handleValue(currentEntityValue)
                        valuesRead++
                        currentEntityValue = null
                    } catch (EntityException e) {
                        throw new SAXException("Error storing entity [${currentEntityValue.getEntityName()}] value: " + e.toString(), e)
                    }
                } else if (currentScs != null) {
                    try {
                        valueHandler.handleService(currentScs)
                        valuesRead++
                        currentScs = null
                    } catch (Exception e) {
                        throw new SAXException("Error running service [${currentScs.getServiceName()}]: " + e.toString(), e)
                    }
                }
            }
        }

        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }
    }

    static class EntityCsvHandler {
        protected EntityDataLoaderImpl edli
        protected ValueHandler valueHandler

        protected long valuesRead = 0
        protected List<String> messageList = new LinkedList()

        EntityCsvHandler(EntityDataLoaderImpl edli, ValueHandler valueHandler) {
            this.edli = edli
            this.valueHandler = valueHandler
        }

        ValueHandler getValueHandler() { return valueHandler }
        long getValuesRead() { return valuesRead }
        List<String> getMessageList() { return messageList }

        boolean loadFile(String location, InputStream is) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))

            CSVParser parser = CSVFormat.newFormat(edli.csvDelimiter)
                    .withCommentStart(edli.csvCommentStart) // is this start of line only? will it interfere with service names?
                    .withQuoteChar(edli.csvQuoteChar)
                    .withSkipHeaderRecord(true) // TODO: remove this? does it even do anything?
                    .withIgnoreEmptyLines(true)
                    .withIgnoreSurroundingSpaces(true)
                    .parse(reader)

            Iterator<CSVRecord> iterator = parser.iterator()

            if (!iterator.hasNext()) throw new BaseException("Not loading file [${location}], no data found")

            CSVRecord firstLineRecord = iterator.next()
            String entityName = firstLineRecord.get(0)
            boolean isService
            if (edli.efi.isEntityDefined(entityName)) {
                isService = false
            } else if (edli.sfi.isServiceDefined(entityName)) {
                isService = true
            } else {
                throw new BaseException("CSV first line first field [${entityName}] is not a valid entity name or service name")
            }

            if (firstLineRecord.size() > 1) {
                // second field is data type
                String type = firstLineRecord.get(1)
                if (type && edli.dataTypes && !edli.dataTypes.contains(type)) {
                    if (logger.isInfoEnabled()) logger.info("Skipping file [${location}], is a type to skip (${type})")
                    return false
                }
            }

            if (!iterator.hasNext()) throw new BaseException("Not loading file [${location}], no second (header) line found")
            CSVRecord headerRecord = iterator.next()
            Map<String, Integer> headerMap = [:]
            for (int i = 0; i < headerRecord.size(); i++) headerMap.put(headerRecord.get(i), i)

            // logger.warn("======== CSV entity/service [${entityName}] headerMap: ${headerMap}")
            while (iterator.hasNext()) {
                CSVRecord record = iterator.next()
                // logger.warn("======== CSV record: ${record.toString()}")
                if (isService) {
                    ServiceCallSyncImpl currentScs = (ServiceCallSyncImpl) edli.sfi.sync().name(entityName)
                    for (Map.Entry<String, Integer> header in headerMap)
                        currentScs.parameter(header.key, record.get((int) header.value))
                    valueHandler.handleService(currentScs)
                    valuesRead++
                } else {
                    EntityValueImpl currentEntityValue = (EntityValueImpl) edli.efi.makeValue(entityName)
                    for (Map.Entry<String, Integer> header in headerMap)
                        currentEntityValue.setString(header.key, record.get((int) header.value))

                    if (!currentEntityValue.containsPrimaryKey()) {
                        if (currentEntityValue.getEntityDefinition().getPkFieldNames().size() == 1) {
                            currentEntityValue.setSequencedIdPrimary()
                        } else {
                            throw new BaseException("Cannot process value with incomplete primary key for [${currentEntityValue.getEntityName()}] with more than 1 primary key field: " + currentEntityValue)
                        }
                    }

                    // logger.warn("======== CSV entity: ${currentEntityValue.toString()}")
                    valueHandler.handleValue(currentEntityValue)
                    valuesRead++
                }
            }
            return true
        }
    }
}
