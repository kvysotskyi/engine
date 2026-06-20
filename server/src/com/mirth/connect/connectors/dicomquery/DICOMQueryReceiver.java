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
import java.util.List;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dcm4che2.data.UID;
import org.dcm4che2.net.Device;
import org.dcm4che2.net.NetworkApplicationEntity;
import org.dcm4che2.net.NetworkConnection;
import org.dcm4che2.net.NewThreadExecutor;
import org.dcm4che2.net.TransferCapability;
import org.dcm4che2.net.service.VerificationService;

import com.mirth.connect.connectors.dicomquery.DICOMQueryReceiverProperties.AcceptedModel;
import com.mirth.connect.donkey.model.event.ConnectionStatusEventType;
import com.mirth.connect.donkey.server.ConnectorTaskException;
import com.mirth.connect.donkey.server.channel.DispatchResult;
import com.mirth.connect.donkey.server.channel.SourceConnector;
import com.mirth.connect.donkey.server.event.ConnectionStatusEvent;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;
import com.mirth.connect.server.util.TemplateValueReplacer;

public class DICOMQueryReceiver extends SourceConnector {

    private static final String[] IVLE_TS = { UID.ImplicitVRLittleEndian };

    private Logger logger = LogManager.getLogger(this.getClass());
    private EventController eventController = ControllerFactory.getFactory().createEventController();
    private TemplateValueReplacer replacer = new TemplateValueReplacer();

    private DICOMQueryReceiverProperties connectorProperties;
    private Device device;
    private NetworkConnection nc;

    @Override
    public void onDeploy() throws ConnectorTaskException {
        this.connectorProperties = (DICOMQueryReceiverProperties) getConnectorProperties();
    }

    @Override
    public void onUndeploy() throws ConnectorTaskException {}

    @Override
    public void onStart() throws ConnectorTaskException {
        String host = replacer.replaceValues(
                connectorProperties.getListenerConnectorProperties().getHost(),
                getChannelId(), getChannel().getName());
        int port = NumberUtils.toInt(replacer.replaceValues(
                connectorProperties.getListenerConnectorProperties().getPort(),
                getChannelId(), getChannel().getName()), 104);
        String aet = StringUtils.defaultIfBlank(
                replacer.replaceValues(connectorProperties.getApplicationEntity(),
                        getChannelId(), getChannel().getName()),
                "DCMQRSCP");

        int idleMs    = NumberUtils.toInt(connectorProperties.getIdleTimeout(), 60000);
        int requestMs = NumberUtils.toInt(connectorProperties.getRequestTimeout(), 5000);
        int releaseMs = NumberUtils.toInt(connectorProperties.getReleaseTimeout(), 5000);

        nc = new NetworkConnection();
        if (StringUtils.isNotBlank(host) && !"0.0.0.0".equals(host)) {
            nc.setHostname(host);
        }
        nc.setPort(port);
        nc.setRequestTimeout(requestMs);
        nc.setReleaseTimeout(releaseMs);

        NetworkApplicationEntity ae = new NetworkApplicationEntity();
        ae.setAETitle(aet);
        ae.setAssociationAcceptor(true);
        ae.setIdleTimeout(idleMs);
        ae.setNetworkConnection(nc);
        ae.setTransferCapability(buildTransferCapabilities(connectorProperties.getAcceptedModel()));

        NewThreadExecutor executor = new NewThreadExecutor(aet);
        MirthCFindService cfindService = new MirthCFindService(
                resolveSopClasses(connectorProperties.getAcceptedModel()), executor, this);

        ae.register(new VerificationService());
        ae.register(cfindService);

        device = new Device(aet);
        device.setNetworkApplicationEntity(ae);
        device.setNetworkConnection(nc);

        try {
            device.startListening(Executors.newCachedThreadPool(
                    r -> new Thread(r, aet + "-worker")));
            logger.info("DICOM C-FIND SCP listening on port " + port + " as " + aet);
            eventController.dispatchEvent(new ConnectionStatusEvent(getChannelId(),
                    getMetaDataId(), getSourceName(), ConnectionStatusEventType.IDLE));
        } catch (Exception e) {
            throw new ConnectorTaskException("Failed to start DICOM C-FIND SCP on port " + port, e);
        }
    }

    @Override
    public void onStop() throws ConnectorTaskException {
        if (device != null) {
            try {
                device.stopListening();
            } catch (Exception e) {
                logger.error("Error stopping DICOM C-FIND SCP", e);
            } finally {
                device = null;
            }
        }
        eventController.dispatchEvent(new ConnectionStatusEvent(getChannelId(), getMetaDataId(),
                getSourceName(), ConnectionStatusEventType.DISCONNECTED));
    }

    @Override
    public void onHalt() throws ConnectorTaskException {
        onStop();
    }

    @Override
    public void handleRecoveredResponse(DispatchResult dispatchResult) {
        finishDispatch(dispatchResult);
    }

    // -------------------------------------------------------------------------
    // SOP class / TransferCapability helpers
    // -------------------------------------------------------------------------

    private String[] resolveSopClasses(AcceptedModel model) {
        switch (model) {
            case STUDY_ROOT:
                return new String[]{ UID.StudyRootQueryRetrieveInformationModelFIND };
            case PATIENT_ROOT:
                return new String[]{ UID.PatientRootQueryRetrieveInformationModelFIND };
            case WORKLIST:
                return new String[]{ UID.ModalityWorklistInformationModelFIND };
            case ALL:
            default:
                return new String[]{
                    UID.StudyRootQueryRetrieveInformationModelFIND,
                    UID.PatientRootQueryRetrieveInformationModelFIND,
                    UID.ModalityWorklistInformationModelFIND
                };
        }
    }

    private TransferCapability[] buildTransferCapabilities(AcceptedModel model) {
        String[] sopClasses = resolveSopClasses(model);
        List<TransferCapability> tcs = new ArrayList<>();
        tcs.add(new TransferCapability(UID.VerificationSOPClass, IVLE_TS, TransferCapability.SCP));
        for (String sop : sopClasses) {
            tcs.add(new TransferCapability(sop, IVLE_TS, TransferCapability.SCP));
        }
        return tcs.toArray(new TransferCapability[0]);
    }
}
