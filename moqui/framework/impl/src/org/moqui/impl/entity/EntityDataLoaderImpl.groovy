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

import org.xml.sax.helpers.DefaultHandler
import org.xml.sax.Attributes
import org.moqui.entity.EntityValue
import org.xml.sax.XMLReader
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.InputSource
import org.moqui.entity.EntityList
import org.moqui.entity.EntityDataLoader

class EntityDataLoaderImpl implements EntityDataLoader {

    protected EntityFacadeImpl efi

    // NOTE: these are Groovy Beans style with no access modifier, results in private fields with implicit getters/setters
    List<String> locationList = new LinkedList<String>()
    String xmlText = null
    Set<String> dataTypes = new HashSet<String>()

    int transactionTimeout = 3600
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
        InputStream inputStream = null
        try {
            // TODO: change to also handle xmlText if present
            // TODO: change to also handle dataTypes if present
            inputStream = efi.ecfi.resourceFacade.getLocationStream(location)
            XMLReader reader = SAXParserFactory.newInstance().newSAXParser().XMLReader
            reader.setContentHandler(exh)
            reader.parse(new InputSource(inputStream))
        } finally {
            if (inputStream) inputStream.close()
        }
    }

    abstract class ValueHandler {
        protected EntityDataLoaderImpl edli
        abstract void handleValue(EntityValue value);
    }
    static class CheckValueHandler extends ValueHandler {
        protected List<String> messageList = new LinkedList()
        CheckValueHandler(EntityDataLoaderImpl edli) { this.edli = edli }
        List<String> getMessageList() { return messageList }
        void handleValue(EntityValue value) {
            // TODO
        }
    }
    static class LoadValueHandler extends ValueHandler {
        LoadValueHandler(EntityDataLoaderImpl edli) { this.edli = edli }
        void handleValue(EntityValue value) {
            // TODO
        }
    }
    static class ListValueHandler extends ValueHandler {
        protected EntityList el
        ListValueHandler(EntityDataLoaderImpl edli) { this.edli = edli; el = new EntityListImpl(edli.efi) }
        void handleValue(EntityValue value) {
            // TODO
        }
        EntityList getEntityList() {return el }
    }

    static class EntityXmlHandler extends DefaultHandler {
        protected EntityDataLoaderImpl edli
        protected ValueHandler valueHandler

        protected EntityValue currentValue = null
        protected CharSequence currentFieldName = null
        protected CharSequence currentFieldValue = null
        protected long valuesRead = 0
        protected List<String> messageList = new LinkedList()

        EntityXmlHandler(EntityDataLoaderImpl edli, ValueHandler valueHandler) {
            this.edli = edli
            this.valueHandler = valueHandler
        }

        ValueHandler getValueHandler() { return valueHandler }
        long getValuesRead() { return valuesRead }
        List<String> getMessageList() { return messageList }

        void startElement(String ns, String localName, String qName, Attributes atts) {
            // TODO
        }
        void characters(char[] chars, int offset, int length) {
            // TODO
            new String(chars, offset, length)
        }
        void endElement(String ns, String localName, String qName) {
            // TODO
        }
    }
}
