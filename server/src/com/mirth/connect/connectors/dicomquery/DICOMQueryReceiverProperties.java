/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dicomquery;

import java.util.Map;

import org.apache.commons.lang3.builder.EqualsBuilder;

import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.donkey.model.channel.ListenerConnectorProperties;
import com.mirth.connect.donkey.model.channel.ListenerConnectorPropertiesInterface;
import com.mirth.connect.donkey.model.channel.SourceConnectorProperties;
import com.mirth.connect.donkey.model.channel.SourceConnectorPropertiesInterface;
import com.mirth.connect.donkey.util.DonkeyElement;
import com.mirth.connect.donkey.util.purge.PurgeUtil;

public class DICOMQueryReceiverProperties extends ConnectorProperties
        implements ListenerConnectorPropertiesInterface, SourceConnectorPropertiesInterface {

    public enum AcceptedModel {
        STUDY_ROOT,
        PATIENT_ROOT,
        WORKLIST,
        ALL
    }

    private ListenerConnectorProperties listenerConnectorProperties;
    private SourceConnectorProperties sourceConnectorProperties;

    private String applicationEntity;
    private AcceptedModel acceptedModel;
    private String idleTimeout;
    private String requestTimeout;
    private String releaseTimeout;

    public DICOMQueryReceiverProperties() {
        listenerConnectorProperties = new ListenerConnectorProperties("104");
        sourceConnectorProperties = new SourceConnectorProperties();

        applicationEntity = "DCMQRSCP";
        acceptedModel = AcceptedModel.ALL;
        idleTimeout = "60000";
        requestTimeout = "5000";
        releaseTimeout = "5000";
    }

    public DICOMQueryReceiverProperties(DICOMQueryReceiverProperties props) {
        super(props);
        ListenerConnectorProperties srcLcp = props.getListenerConnectorProperties();
        listenerConnectorProperties = new ListenerConnectorProperties(srcLcp.getPort());
        listenerConnectorProperties.setHost(srcLcp.getHost());

        SourceConnectorProperties srcScp = props.getSourceConnectorProperties();
        sourceConnectorProperties = new SourceConnectorProperties(srcScp.getResponseVariable());
        sourceConnectorProperties.setRespondAfterProcessing(srcScp.isRespondAfterProcessing());
        sourceConnectorProperties.setProcessBatch(srcScp.isProcessBatch());
        sourceConnectorProperties.setFirstResponse(srcScp.isFirstResponse());
        sourceConnectorProperties.setProcessingThreads(srcScp.getProcessingThreads());
        sourceConnectorProperties.setResourceIds(new java.util.LinkedHashMap<>(srcScp.getResourceIds()));
        sourceConnectorProperties.setQueueBufferSize(srcScp.getQueueBufferSize());

        applicationEntity = props.getApplicationEntity();
        acceptedModel = props.getAcceptedModel();
        idleTimeout = props.getIdleTimeout();
        requestTimeout = props.getRequestTimeout();
        releaseTimeout = props.getReleaseTimeout();
    }

    public String getApplicationEntity() { return applicationEntity; }
    public void setApplicationEntity(String applicationEntity) {
        this.applicationEntity = applicationEntity;
    }

    public AcceptedModel getAcceptedModel() { return acceptedModel; }
    public void setAcceptedModel(AcceptedModel acceptedModel) {
        this.acceptedModel = acceptedModel;
    }

    public String getIdleTimeout() { return idleTimeout; }
    public void setIdleTimeout(String idleTimeout) { this.idleTimeout = idleTimeout; }

    public String getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(String requestTimeout) { this.requestTimeout = requestTimeout; }

    public String getReleaseTimeout() { return releaseTimeout; }
    public void setReleaseTimeout(String releaseTimeout) { this.releaseTimeout = releaseTimeout; }

    @Override
    public ListenerConnectorProperties getListenerConnectorProperties() {
        return listenerConnectorProperties;
    }

    @Override
    public SourceConnectorProperties getSourceConnectorProperties() {
        return sourceConnectorProperties;
    }

    @Override
    public String getProtocol() { return "DICOMQUERY"; }

    @Override
    public String getName() { return "DICOM C-FIND SCP"; }

    @Override
    public String toFormattedString() { return null; }

    @Override
    public boolean canBatch() { return false; }

    @Override
    public ConnectorProperties clone() { return new DICOMQueryReceiverProperties(this); }

    @Override
    public boolean equals(Object obj) { return EqualsBuilder.reflectionEquals(this, obj); }

    // @formatter:off
    @Override public void migrate3_0_1(DonkeyElement element) {}
    @Override public void migrate3_0_2(DonkeyElement element) {}
    @Override public void migrate3_1_0(DonkeyElement element) { super.migrate3_1_0(element); }
    @Override public void migrate3_2_0(DonkeyElement element) {}
    @Override public void migrate3_3_0(DonkeyElement element) {}
    @Override public void migrate3_4_0(DonkeyElement element) {}
    @Override public void migrate3_5_0(DonkeyElement element) {}
    @Override public void migrate3_6_0(DonkeyElement element) {}
    @Override public void migrate3_7_0(DonkeyElement element) {}
    @Override public void migrate3_9_0(DonkeyElement element) {}
    @Override public void migrate3_11_0(DonkeyElement element) {}
    @Override public void migrate3_11_1(DonkeyElement element) {}
    @Override public void migrate3_12_0(DonkeyElement element) {} // @formatter:on

    @Override
    public Map<String, Object> getPurgedProperties() {
        Map<String, Object> purgedProperties = super.getPurgedProperties();
        purgedProperties.put("sourceConnectorProperties",
                sourceConnectorProperties.getPurgedProperties());
        purgedProperties.put("acceptedModel", acceptedModel);
        purgedProperties.put("idleTimeout", PurgeUtil.getNumericValue(idleTimeout));
        purgedProperties.put("requestTimeout", PurgeUtil.getNumericValue(requestTimeout));
        purgedProperties.put("releaseTimeout", PurgeUtil.getNumericValue(releaseTimeout));
        return purgedProperties;
    }
}
