/*
 * This software is in the public domain under CC0 1.0 Universal.
 * 
 * To the extent possible under law, the author(s) have dedicated all
 * copyright and related and neighboring rights to this software to the
 * public domain worldwide. This software is distributed without any
 * warranty.
 * 
 * You should have received a copy of the CC0 Public Domain Dedication
 * along with this software (see the LICENSE.md file). If not, see
 * <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.moqui.impl.util

import org.moqui.context.ExecutionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.regex.Pattern

class EdiHandler {
    protected final static Logger logger = LoggerFactory.getLogger(EdiHandler.class)

    protected ExecutionContext ec

    char segmentTerminator = '~'
    char elementSeparator = '*'
    char compositeDelimiter = '@'

    protected List<Map<String, Object>> envelope = null
    protected List<Map<String, Object>> body = null
    // FUTURE: load Bots record defs to validate input/output messages: Map<String, List> recordDefs

    EdiHandler(ExecutionContext ec) {
        this.ec = ec
    }

    EdiHandler setChars(char segmentTerminator, char elementSeparator, char compositeDelimiter) {
        this.segmentTerminator = segmentTerminator
        this.elementSeparator = elementSeparator
        this.compositeDelimiter = compositeDelimiter
        return this
    }

    /** Run a Groovy script at location to get the nested List/Map file envelope structure (for X12: ISA, GS, and ST
     * segments). The QUERIES and SUBTRANSLATION entries can be removed, will be ignored.
     *
     * These are based on Bots Grammars (http://sourceforge.net/projects/bots/files/grammars/), converted from Python to
     * Groovy List/Map syntax (search/replace '{' to '[' and '}' to ']'), only include the structure List (script should
     * evaluate to or return just the structure List).
     */
    EdiHandler loadEnvelope(String location) {
        envelope = (List<Map<String, Object>>) ec.resource.script(location, null)
        return this
    }

    /** Run a Groovy script at location to get the nested List/Map file structure. The segment(s) in the top-level List
     * should be referenced in the envelope structure, ie this structure will be used under the envelope structure.
     *
     * These are based on Bots Grammars (http://sourceforge.net/projects/bots/files/grammars/), converted from Python to
     * Groovy List/Map syntax (search/replace '{' to '[' and '}' to ']'), only include the structure List (script should
     * evaluate to or return just the structure List).
     */
    EdiHandler loadBody(String location) {
        body = (List<Map<String, Object>>) ec.resource.script(location, null)
        return this
    }

    /** Parse EDI text and return a Map containing a "elements" entry with a List of element values (each may be a String
     * or List<String>) and an entry for each child segment where the key is the segment ID (generally 2 or 3 characters)
     * and the value is a List<Map> where each Map has this same Map structure.
     *
     * If no definition is found for a segment, all text the segment (in original form) is put in the "originalList"
     * (of type List<String>) entry. This is used for partial parsing (envelope only) and then completing the parse with
     * the body structure loaded.
     */
    Map<String, Object> parseText(String ediText) {
        // TODO: auto-detect segment/element/composite chars? only if not set, if can't use defaults?
        // TODO: For X12: if root segment is ISA determine compositeDelimeter from ISA16
        // TODO: if fileStructure == null just read segments defined in envelope

        List<String> allSegmentStringList = Arrays.asList(ediText.split(Pattern.quote(segmentTerminator as String)))

        Map<String, Object> rootSegment = [:]
        parseSegment(allSegmentStringList, 0, rootSegment, envelope)
        return rootSegment
    }

    /** Internal recursive method for parsing segments */
    protected void parseSegment(List<String> allSegmentStringList, int segmentIndex, Map<String, Object> currentSegment,
                                List<Map<String, Object>> levelDefList) {
        // TODO
    }

    List<String> splitMessage(String rootHeaderId, String rootTrailerId, String ediText) {
        List<String> splitStringList = []
        List<String> allSegmentStringList = Arrays.asList(ediText.split(Pattern.quote(segmentTerminator as String)))

        ArrayList<String> curSplitList = null
        for (int i = 0; i < allSegmentStringList.size(); i++) {
            String segmentString = allSegmentStringList.get(i)
            String segId = segmentString.substring(0, segmentString.indexOf(elementSeparator))

            if (rootHeaderId && segId == rootHeaderId && curSplitList) {
                // hit a header without a footer, save what we have so far and start a new split
                splitStringList.add(combineSegments(curSplitList))
                curSplitList = new ArrayList<>()
                curSplitList.add(segmentString)
            } else if (rootTrailerId && segId == rootTrailerId) {
                // hit a trailer, add it to the current split, save the split, clear the current split
                if (curSplitList == null) curSplitList = new ArrayList<>()
                curSplitList.add(segmentString)
                splitStringList.add(combineSegments(curSplitList))
                curSplitList = null
            } else {
                if (curSplitList == null) curSplitList = new ArrayList<>()
                curSplitList.add(segmentString)
            }
        }

        return splitStringList
    }
    String combineSegments(ArrayList<String> segmentStringList) {
        StringBuilder sb = new StringBuilder()
        for (int i = 0; i < segmentStringList.size(); i++) sb.append(segmentStringList.get(i)).append(segmentTerminator)
        return sb.toString()
    }


    protected String escape(String original) {
        // TODO
        return original
    }
    protected String unescape(String original) {
        // TODO
        return original
    }
}
