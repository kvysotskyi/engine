/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dicomquery;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.UID;
import org.dcm4che2.data.VR;
import org.dcm4che2.net.Association;
import org.dcm4che2.net.CommandUtils;
import org.dcm4che2.net.Device;
import org.dcm4che2.net.DimseRSP;
import org.dcm4che2.net.NetworkApplicationEntity;
import org.dcm4che2.net.NetworkConnection;
import org.dcm4che2.net.NewThreadExecutor;
import org.dcm4che2.net.TransferCapability;

import com.mirth.connect.connectors.dicomquery.DICOMQueryDispatcherProperties.QueryLevel;
import com.mirth.connect.connectors.dicomquery.DICOMQueryDispatcherProperties.QueryModel;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.donkey.model.event.ConnectionStatusEventType;
import com.mirth.connect.donkey.model.event.ErrorEventType;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Response;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.server.ConnectorTaskException;
import com.mirth.connect.donkey.server.channel.DestinationConnector;
import com.mirth.connect.donkey.server.event.ConnectionStatusEvent;
import com.mirth.connect.donkey.server.event.ErrorEvent;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;
import com.mirth.connect.server.util.TemplateValueReplacer;
import com.mirth.connect.util.ErrorMessageBuilder;

public class DICOMQueryDispatcher extends DestinationConnector {

    private static final String[] IVLE_TS = { UID.ImplicitVRLittleEndian };

    private Logger logger = LogManager.getLogger(this.getClass());
    private EventController eventController = ControllerFactory.getFactory().createEventController();
    private TemplateValueReplacer replacer = new TemplateValueReplacer();

    @Override
    public void onDeploy() throws ConnectorTaskException {}

    @Override
    public void onStart() throws ConnectorTaskException {}

    @Override
    public void onStop() throws ConnectorTaskException {}

    @Override
    public void onHalt() throws ConnectorTaskException {}

    @Override
    public void onUndeploy() throws ConnectorTaskException {}

    @Override
    public void replaceConnectorProperties(ConnectorProperties connectorProperties,
            ConnectorMessage connectorMessage) {
        DICOMQueryDispatcherProperties p = (DICOMQueryDispatcherProperties) connectorProperties;
        p.setHost(replacer.replaceValues(p.getHost(), connectorMessage));
        p.setPort(replacer.replaceValues(p.getPort(), connectorMessage));
        p.setCalledAet(replacer.replaceValues(p.getCalledAet(), connectorMessage));
        p.setCallingAet(replacer.replaceValues(p.getCallingAet(), connectorMessage));
        p.setPatientId(replacer.replaceValues(p.getPatientId(), connectorMessage));
        p.setPatientName(replacer.replaceValues(p.getPatientName(), connectorMessage));
        p.setStudyDate(replacer.replaceValues(p.getStudyDate(), connectorMessage));
        p.setModality(replacer.replaceValues(p.getModality(), connectorMessage));
        p.setAccessionNumber(replacer.replaceValues(p.getAccessionNumber(), connectorMessage));
        p.setStudyInstanceUid(replacer.replaceValues(p.getStudyInstanceUid(), connectorMessage));
        p.setSeriesInstanceUid(replacer.replaceValues(p.getSeriesInstanceUid(), connectorMessage));
        p.setSopInstanceUid(replacer.replaceValues(p.getSopInstanceUid(), connectorMessage));
        p.setScheduledDate(replacer.replaceValues(p.getScheduledDate(), connectorMessage));
        p.setScheduledAet(replacer.replaceValues(p.getScheduledAet(), connectorMessage));
    }

    @Override
    public Response send(ConnectorProperties connectorProperties,
            ConnectorMessage connectorMessage) {
        DICOMQueryDispatcherProperties props = (DICOMQueryDispatcherProperties) connectorProperties;

        eventController.dispatchEvent(new ConnectionStatusEvent(getChannelId(), getMetaDataId(),
                getDestinationName(), ConnectionStatusEventType.WRITING,
                "C-FIND → " + props.getHost() + ":" + props.getPort()
                        + " [" + props.getCalledAet() + "]"));

        String responseData = null;
        String responseError = null;
        String responseStatusMessage = null;
        Status responseStatus = Status.QUEUED;

        Association assoc = null;
        try {
            assoc = openAssociation(props);

            String sopClass = resolveSopClass(props.getQueryModel());
            DicomObject keys = buildQueryKeys(props);

            DimseRSP rsp = assoc.cfind(sopClass, CommandUtils.NORMAL, keys,
                    UID.ImplicitVRLittleEndian, 0);

            List<DicomObject> results = new ArrayList<>();
            while (rsp.next()) {
                DicomObject dataset = rsp.getDataset();
                if (dataset != null) {
                    results.add(dataset);
                }
            }

            responseData = serializeToXml(results);
            responseStatus = Status.SENT;
            responseStatusMessage = "C-FIND completed: " + results.size() + " result(s)";

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseStatus = Status.QUEUED;
            responseStatusMessage = "C-FIND interrupted";
        } catch (Exception e) {
            responseStatusMessage = ErrorMessageBuilder.buildErrorResponse(e.getMessage(), e);
            responseError = ErrorMessageBuilder.buildErrorMessage(
                    connectorProperties.getName(), e.getMessage(), null);
            eventController.dispatchEvent(new ErrorEvent(getChannelId(), getMetaDataId(),
                    connectorMessage.getMessageId(), ErrorEventType.DESTINATION_CONNECTOR,
                    getDestinationName(), connectorProperties.getName(), e.getMessage(), null));
        } finally {
            if (assoc != null) {
                try { assoc.release(false); } catch (Exception e) {
                    logger.warn("Error releasing DICOM association", e);
                }
            }
            eventController.dispatchEvent(new ConnectionStatusEvent(getChannelId(), getMetaDataId(),
                    getDestinationName(), ConnectionStatusEventType.IDLE));
        }

        return new Response(responseStatus, responseData, responseStatusMessage, responseError);
    }

    // -------------------------------------------------------------------------
    // Association setup
    // -------------------------------------------------------------------------

    private Association openAssociation(DICOMQueryDispatcherProperties props) throws Exception {
        String callingAet = StringUtils.defaultIfBlank(props.getCallingAet(), "DCMQRSCU");
        String calledAet  = StringUtils.defaultIfBlank(props.getCalledAet(), "DCMQRSCP");
        int port          = NumberUtils.toInt(props.getPort(), 104);
        int connectMs     = NumberUtils.toInt(props.getConnectTimeout(), 5000);
        int responseMs    = NumberUtils.toInt(props.getResponseTimeout(), 60000);

        NetworkConnection localConn  = new NetworkConnection();
        localConn.setConnectTimeout(connectMs);
        NetworkConnection remoteConn = new NetworkConnection();
        remoteConn.setHostname(props.getHost());
        remoteConn.setPort(port);

        NetworkApplicationEntity remoteAE = new NetworkApplicationEntity();
        remoteAE.setAETitle(calledAet);
        remoteAE.setInstalled(true);
        remoteAE.setAssociationAcceptor(true);
        remoteAE.setNetworkConnection(new NetworkConnection[]{ remoteConn });

        NetworkApplicationEntity localAE = new NetworkApplicationEntity();
        localAE.setAETitle(callingAet);
        localAE.setAssociationInitiator(true);
        localAE.setNetworkConnection(new NetworkConnection[]{ localConn });

        // Offer SCU transfer capability for the chosen SOP class
        String sopClass = resolveSopClass(props.getQueryModel());
        TransferCapability tc = new TransferCapability(sopClass, IVLE_TS, TransferCapability.SCU);
        localAE.setTransferCapability(new TransferCapability[]{ tc });
        localAE.setDimseRspTimeout(responseMs);

        Device device = new Device(callingAet);
        device.setNetworkApplicationEntity(localAE);
        device.setNetworkConnection(localConn);

        Executor executor = new NewThreadExecutor(callingAet);
        return localAE.connect(remoteAE, executor);
    }

    // -------------------------------------------------------------------------
    // SOP class resolution
    // -------------------------------------------------------------------------

    private String resolveSopClass(QueryModel model) {
        switch (model) {
            case PATIENT_ROOT:
                return UID.PatientRootQueryRetrieveInformationModelFIND;
            case WORKLIST:
                return UID.ModalityWorklistInformationModelFIND;
            case STUDY_ROOT:
            default:
                return UID.StudyRootQueryRetrieveInformationModelFIND;
        }
    }

    // -------------------------------------------------------------------------
    // Query key construction
    // -------------------------------------------------------------------------

    private DicomObject buildQueryKeys(DICOMQueryDispatcherProperties props) {
        DicomObject keys = new BasicDicomObject();

        if (props.getQueryModel() == QueryModel.WORKLIST) {
            buildWorklistKeys(keys, props);
        } else {
            buildHierarchicalKeys(keys, props);
        }
        return keys;
    }

    private void buildHierarchicalKeys(DicomObject keys, DICOMQueryDispatcherProperties props) {
        String levelStr;
        switch (props.getQueryLevel()) {
            case PATIENT: levelStr = "PATIENT"; break;
            case SERIES:  levelStr = "SERIES";  break;
            case IMAGE:   levelStr = "IMAGE";   break;
            case STUDY:
            default:      levelStr = "STUDY";   break;
        }
        keys.putString(Tag.QueryRetrieveLevel, VR.CS, levelStr);

        // Each attribute: non-empty value = filter; empty string = "return this attribute"
        putKey(keys, Tag.PatientID, VR.LO, props.getPatientId());
        putKey(keys, Tag.PatientName, VR.PN, props.getPatientName());
        putKey(keys, Tag.StudyDate, VR.DA, props.getStudyDate());
        putKey(keys, Tag.AccessionNumber, VR.SH, props.getAccessionNumber());
        putKey(keys, Tag.StudyInstanceUID, VR.UI, props.getStudyInstanceUid());
        keys.putString(Tag.NumberOfStudyRelatedSeries, VR.IS, "");
        keys.putString(Tag.NumberOfStudyRelatedInstances, VR.IS, "");

        if (props.getQueryLevel() == QueryLevel.SERIES
                || props.getQueryLevel() == QueryLevel.IMAGE) {
            putKey(keys, Tag.Modality, VR.CS, props.getModality());
            putKey(keys, Tag.SeriesInstanceUID, VR.UI, props.getSeriesInstanceUid());
            keys.putString(Tag.NumberOfSeriesRelatedInstances, VR.IS, "");
        }

        if (props.getQueryLevel() == QueryLevel.IMAGE) {
            putKey(keys, Tag.SOPInstanceUID, VR.UI, props.getSopInstanceUid());
            putKey(keys, Tag.SOPClassUID, VR.UI, "");
        }
    }

    private void buildWorklistKeys(DicomObject keys, DICOMQueryDispatcherProperties props) {
        putKey(keys, Tag.PatientID, VR.LO, props.getPatientId());
        putKey(keys, Tag.PatientName, VR.PN, props.getPatientName());
        putKey(keys, Tag.AccessionNumber, VR.SH, props.getAccessionNumber());
        keys.putString(Tag.PatientBirthDate, VR.DA, "");
        keys.putString(Tag.PatientSex, VR.CS, "");
        keys.putString(Tag.RequestedProcedureID, VR.SH, "");
        keys.putString(Tag.RequestedProcedureDescription, VR.LO, "");

        DicomObject spsItem = new BasicDicomObject();
        putKey(spsItem, Tag.ScheduledProcedureStepStartDate, VR.DA, props.getScheduledDate());
        putKey(spsItem, Tag.ScheduledStationAETitle, VR.AE, props.getScheduledAet());
        putKey(spsItem, Tag.Modality, VR.CS, props.getModality());
        spsItem.putString(Tag.ScheduledProcedureStepDescription, VR.LO, "");
        spsItem.putString(Tag.ScheduledPerformingPhysicianName, VR.PN, "");
        keys.putNestedDicomObject(Tag.ScheduledProcedureStepSequence, spsItem);
    }

    /** Puts tag with the given value; empty/blank value puts an empty string (return-key). */
    private void putKey(DicomObject obj, int tag, VR vr, String value) {
        obj.putString(tag, vr, StringUtils.defaultString(value));
    }

    // -------------------------------------------------------------------------
    // XML serialization
    // -------------------------------------------------------------------------

    private String serializeToXml(List<DicomObject> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("<cfindrsp count=\"").append(results.size()).append("\">\n");
        for (DicomObject obj : results) {
            sb.append("  <item>\n");
            appendDicomObject(sb, obj, "    ");
            sb.append("  </item>\n");
        }
        sb.append("</cfindrsp>");
        return sb.toString();
    }

    private void appendDicomObject(StringBuilder sb, DicomObject obj, String indent) {
        if (obj == null) return;
        Iterator<DicomElement> it = obj.iterator();
        while (it.hasNext()) {
            DicomElement elem = it.next();
            int tag = elem.tag();
            String tagHex = String.format("%08X", tag);
            String vrName = elem.vr() != null ? elem.vr().toString() : "UN";

            if (elem.hasDicomObjects()) {
                sb.append(indent).append("<attr tag=\"").append(tagHex)
                  .append("\" vr=\"").append(vrName).append("\">\n");
                for (int i = 0; i < elem.countItems(); i++) {
                    DicomObject nested = elem.getDicomObject(i);
                    sb.append(indent).append("  <item>\n");
                    appendDicomObject(sb, nested, indent + "    ");
                    sb.append(indent).append("  </item>\n");
                }
                sb.append(indent).append("</attr>\n");
            } else {
                String value = elem.getValueAsString(null, 0);
                if (value == null) value = "";
                sb.append(indent).append("<attr tag=\"").append(tagHex)
                  .append("\" vr=\"").append(vrName).append("\">")
                  .append(escapeXml(value))
                  .append("</attr>\n");
            }
        }
    }

    private String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
