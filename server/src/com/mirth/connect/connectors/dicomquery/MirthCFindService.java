/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dicomquery;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.VR;
import org.dcm4che2.net.Association;
import org.dcm4che2.net.CommandUtils;
import org.dcm4che2.net.DicomServiceException;
import org.dcm4che2.net.DimseRSP;
import org.dcm4che2.net.Status;
import org.dcm4che2.net.service.CFindService;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.mirth.connect.donkey.model.message.RawMessage;
import com.mirth.connect.donkey.server.channel.DispatchResult;
import com.mirth.connect.donkey.server.channel.SourceConnector;

/**
 * C-FIND SCP service that dispatches queries to the Mirth channel and streams
 * results back as C-FIND-RSP PENDING items.
 *
 * The channel receives an XML representation of the query keys and should return
 * a {@code <cfindrsp>} XML document with {@code <item>} children, each containing
 * {@code <attr>} elements (same format as DICOMQueryDispatcher output).
 */
class MirthCFindService extends CFindService {

    private final Logger logger = LogManager.getLogger(getClass());
    private final SourceConnector sourceConnector;

    MirthCFindService(String[] sopClasses, Executor executor, SourceConnector sourceConnector) {
        super(sopClasses, executor);
        this.sourceConnector = sourceConnector;
    }

    @Override
    public void cfind(Association as, int pcid, DicomObject rq, DicomObject queryData)
            throws DicomServiceException, IOException {

        String xml = dicomObjectToXml(queryData);
        Map<String, Object> sourceMap = buildSourceMap(as);

        DispatchResult dispatchResult = null;
        try {
            dispatchResult = sourceConnector.dispatchRawMessage(
                    new RawMessage(xml, null, sourceMap));

            List<DicomObject> results = parseResponse(dispatchResult);

            for (DicomObject dataset : results) {
                as.writeDimseRSP(pcid, CommandUtils.mkRSP(rq, CommandUtils.PENDING), dataset);
            }

            as.writeDimseRSP(pcid, CommandUtils.mkRSP(rq, CommandUtils.SUCCESS));

        } catch (DicomServiceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DicomServiceException(rq, Status.ProcessingFailure, "Interrupted");
        } catch (Exception e) {
            logger.error("Error processing C-FIND request", e);
            throw new DicomServiceException(rq, Status.ProcessingFailure, e.getMessage());
        } finally {
            if (dispatchResult != null) {
                sourceConnector.finishDispatch(dispatchResult);
            }
        }
    }

    // CFindService.doCFind is not called since we override cfind() directly.
    @Override
    protected DimseRSP doCFind(Association as, int pcid, DicomObject rq, DicomObject data,
            DicomObject rsp) throws DicomServiceException {
        return null;
    }

    // -------------------------------------------------------------------------
    // Query serialization (DicomObject → XML string sent to channel)
    // -------------------------------------------------------------------------

    private String dicomObjectToXml(DicomObject obj) {
        StringBuilder sb = new StringBuilder("<cfindreq>\n");
        appendAttrs(sb, obj, "  ");
        sb.append("</cfindreq>");
        return sb.toString();
    }

    private void appendAttrs(StringBuilder sb, DicomObject obj, String indent) {
        if (obj == null) return;
        Iterator<DicomElement> it = obj.iterator();
        while (it.hasNext()) {
            DicomElement elem = it.next();
            String tagHex = String.format("%08X", elem.tag());
            String vrName = elem.vr() != null ? elem.vr().toString() : "UN";

            if (elem.hasDicomObjects()) {
                sb.append(indent).append("<attr tag=\"").append(tagHex)
                  .append("\" vr=\"").append(vrName).append("\">\n");
                for (int i = 0; i < elem.countItems(); i++) {
                    sb.append(indent).append("  <item>\n");
                    appendAttrs(sb, elem.getDicomObject(i), indent + "    ");
                    sb.append(indent).append("  </item>\n");
                }
                sb.append(indent).append("</attr>\n");
            } else {
                String value = elem.getValueAsString(null, 0);
                if (value == null) value = "";
                sb.append(indent).append("<attr tag=\"").append(tagHex)
                  .append("\" vr=\"").append(vrName).append("\">")
                  .append(escapeXml(value)).append("</attr>\n");
            }
        }
    }

    private String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // -------------------------------------------------------------------------
    // Response parsing (channel XML → List<DicomObject>)
    // -------------------------------------------------------------------------

    private List<DicomObject> parseResponse(DispatchResult dispatchResult) throws Exception {
        List<DicomObject> results = new ArrayList<>();
        if (dispatchResult == null || dispatchResult.getSelectedResponse() == null) {
            return results;
        }

        String responseContent = dispatchResult.getSelectedResponse().getMessage();
        if (responseContent == null || responseContent.isEmpty()) {
            return results;
        }

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(
                responseContent.getBytes(StandardCharsets.UTF_8)));

        NodeList items = doc.getElementsByTagName("item");
        for (int i = 0; i < items.getLength(); i++) {
            Element itemEl = (Element) items.item(i);
            // Only process top-level <item> elements (children of root, not nested in SQ)
            if (itemEl.getParentNode().isSameNode(doc.getDocumentElement())) {
                BasicDicomObject dicomObj = new BasicDicomObject();
                parseAttrElements(itemEl, dicomObj);
                results.add(dicomObj);
            }
        }
        return results;
    }

    private void parseAttrElements(Element parent, DicomObject obj) {
        NodeList attrs = parent.getChildNodes();
        for (int i = 0; i < attrs.getLength(); i++) {
            if (!(attrs.item(i) instanceof Element)) continue;
            Element attrEl = (Element) attrs.item(i);
            if (!"attr".equals(attrEl.getLocalName()) && !"attr".equals(attrEl.getNodeName())) {
                continue;
            }

            String tagHex = attrEl.getAttribute("tag");
            String vrStr  = attrEl.getAttribute("vr");
            if (tagHex == null || tagHex.isEmpty()) continue;

            int tag;
            try {
                tag = (int) Long.parseLong(tagHex, 16);
            } catch (NumberFormatException e) {
                logger.warn("Skipping unrecognized tag hex: " + tagHex);
                continue;
            }

            VR vr = resolveVr(vrStr, tag);

            // Check for nested items (SQ)
            NodeList nestedItems = attrEl.getElementsByTagName("item");
            if (nestedItems.getLength() > 0
                    && nestedItems.item(0).getParentNode().isSameNode(attrEl)) {
                // Sequence — take the first item only (C-FIND typically has one SPS item)
                Element nestedItemEl = (Element) nestedItems.item(0);
                BasicDicomObject nested = new BasicDicomObject();
                parseAttrElements(nestedItemEl, nested);
                obj.putNestedDicomObject(tag, nested);
            } else {
                String value = attrEl.getTextContent();
                obj.putString(tag, vr, value != null ? value : "");
            }
        }
    }

    private VR resolveVr(String vrStr, int tag) {
        if (vrStr != null && !vrStr.isEmpty()) {
            try {
                java.lang.reflect.Field f = VR.class.getField(vrStr);
                return (VR) f.get(null);
            } catch (Exception e) {
                // fall through to default
            }
        }
        return VR.LO;
    }

    // -------------------------------------------------------------------------
    // Source map
    // -------------------------------------------------------------------------

    private Map<String, Object> buildSourceMap(Association as) {
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("callingAET", as.getRemoteAET());
        sourceMap.put("calledAET", as.getLocalAET());
        if (as.getSocket() != null) {
            sourceMap.put("remoteAddress",
                    as.getSocket().getInetAddress().getHostAddress());
            sourceMap.put("remotePort", as.getSocket().getPort());
            if (as.getSocket().getRemoteSocketAddress() instanceof InetSocketAddress) {
                InetSocketAddress remote = (InetSocketAddress) as.getSocket().getRemoteSocketAddress();
                sourceMap.put("remoteHost", remote.getHostString());
            }
        }
        return sourceMap;
    }
}
