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
import com.mirth.connect.donkey.model.channel.DestinationConnectorProperties;
import com.mirth.connect.donkey.model.channel.DestinationConnectorPropertiesInterface;
import com.mirth.connect.donkey.util.DonkeyElement;
import com.mirth.connect.donkey.util.purge.PurgeUtil;

public class DICOMQueryDispatcherProperties extends ConnectorProperties
        implements DestinationConnectorPropertiesInterface {

    public enum QueryModel {
        STUDY_ROOT,
        PATIENT_ROOT,
        WORKLIST
    }

    public enum QueryLevel {
        PATIENT,
        STUDY,
        SERIES,
        IMAGE
    }

    private DestinationConnectorProperties destinationConnectorProperties;

    // Network
    private String host;
    private String port;
    private String calledAet;
    private String callingAet;

    // Query
    private QueryModel queryModel;
    private QueryLevel queryLevel;

    // Common patient/study filter keys (template-replaceable)
    private String patientId;
    private String patientName;
    private String studyDate;
    private String modality;
    private String accessionNumber;
    private String studyInstanceUid;
    private String seriesInstanceUid;
    private String sopInstanceUid;

    // Worklist-specific
    private String scheduledDate;
    private String scheduledAet;

    // Timeouts
    private String connectTimeout;
    private String responseTimeout;

    public DICOMQueryDispatcherProperties() {
        destinationConnectorProperties = new DestinationConnectorProperties();

        host = "localhost";
        port = "104";
        calledAet = "DCMQRSCP";
        callingAet = "DCMQRSCU";

        queryModel = QueryModel.STUDY_ROOT;
        queryLevel = QueryLevel.STUDY;

        patientId = "";
        patientName = "";
        studyDate = "";
        modality = "";
        accessionNumber = "";
        studyInstanceUid = "";
        seriesInstanceUid = "";
        sopInstanceUid = "";

        scheduledDate = "";
        scheduledAet = "";

        connectTimeout = "5000";
        responseTimeout = "60000";
    }

    public DICOMQueryDispatcherProperties(DICOMQueryDispatcherProperties props) {
        super(props);
        destinationConnectorProperties = new DestinationConnectorProperties(
                props.getDestinationConnectorProperties());

        host = props.getHost();
        port = props.getPort();
        calledAet = props.getCalledAet();
        callingAet = props.getCallingAet();

        queryModel = props.getQueryModel();
        queryLevel = props.getQueryLevel();

        patientId = props.getPatientId();
        patientName = props.getPatientName();
        studyDate = props.getStudyDate();
        modality = props.getModality();
        accessionNumber = props.getAccessionNumber();
        studyInstanceUid = props.getStudyInstanceUid();
        seriesInstanceUid = props.getSeriesInstanceUid();
        sopInstanceUid = props.getSopInstanceUid();

        scheduledDate = props.getScheduledDate();
        scheduledAet = props.getScheduledAet();

        connectTimeout = props.getConnectTimeout();
        responseTimeout = props.getResponseTimeout();
    }

    // --- getters / setters ---

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public String getPort() { return port; }
    public void setPort(String port) { this.port = port; }

    public String getCalledAet() { return calledAet; }
    public void setCalledAet(String calledAet) { this.calledAet = calledAet; }

    public String getCallingAet() { return callingAet; }
    public void setCallingAet(String callingAet) { this.callingAet = callingAet; }

    public QueryModel getQueryModel() { return queryModel; }
    public void setQueryModel(QueryModel queryModel) { this.queryModel = queryModel; }

    public QueryLevel getQueryLevel() { return queryLevel; }
    public void setQueryLevel(QueryLevel queryLevel) { this.queryLevel = queryLevel; }

    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getStudyDate() { return studyDate; }
    public void setStudyDate(String studyDate) { this.studyDate = studyDate; }

    public String getModality() { return modality; }
    public void setModality(String modality) { this.modality = modality; }

    public String getAccessionNumber() { return accessionNumber; }
    public void setAccessionNumber(String accessionNumber) { this.accessionNumber = accessionNumber; }

    public String getStudyInstanceUid() { return studyInstanceUid; }
    public void setStudyInstanceUid(String studyInstanceUid) { this.studyInstanceUid = studyInstanceUid; }

    public String getSeriesInstanceUid() { return seriesInstanceUid; }
    public void setSeriesInstanceUid(String seriesInstanceUid) { this.seriesInstanceUid = seriesInstanceUid; }

    public String getSopInstanceUid() { return sopInstanceUid; }
    public void setSopInstanceUid(String sopInstanceUid) { this.sopInstanceUid = sopInstanceUid; }

    public String getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(String scheduledDate) { this.scheduledDate = scheduledDate; }

    public String getScheduledAet() { return scheduledAet; }
    public void setScheduledAet(String scheduledAet) { this.scheduledAet = scheduledAet; }

    public String getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(String connectTimeout) { this.connectTimeout = connectTimeout; }

    public String getResponseTimeout() { return responseTimeout; }
    public void setResponseTimeout(String responseTimeout) { this.responseTimeout = responseTimeout; }

    @Override
    public DestinationConnectorProperties getDestinationConnectorProperties() {
        return destinationConnectorProperties;
    }

    @Override
    public String getProtocol() { return "DICOMQUERY"; }

    @Override
    public String getName() { return "DICOM C-FIND SCU"; }

    @Override
    public String toFormattedString() {
        StringBuilder b = new StringBuilder();
        b.append("HOST: ").append(host).append(":").append(port).append("\n");
        b.append("CALLED AET: ").append(calledAet).append("\n");
        b.append("CALLING AET: ").append(callingAet).append("\n");
        b.append("QUERY MODEL: ").append(queryModel).append("\n");
        if (queryModel != QueryModel.WORKLIST) {
            b.append("QUERY LEVEL: ").append(queryLevel).append("\n");
        }
        if (!patientId.isEmpty()) b.append("PATIENT ID: ").append(patientId).append("\n");
        if (!patientName.isEmpty()) b.append("PATIENT NAME: ").append(patientName).append("\n");
        if (!studyDate.isEmpty()) b.append("STUDY DATE: ").append(studyDate).append("\n");
        if (!modality.isEmpty()) b.append("MODALITY: ").append(modality).append("\n");
        if (!accessionNumber.isEmpty()) b.append("ACCESSION: ").append(accessionNumber).append("\n");
        return b.toString();
    }

    @Override
    public ConnectorProperties clone() { return new DICOMQueryDispatcherProperties(this); }

    @Override
    public boolean canValidateResponse() { return false; }

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
        purgedProperties.put("destinationConnectorProperties",
                destinationConnectorProperties.getPurgedProperties());
        purgedProperties.put("queryModel", queryModel);
        purgedProperties.put("queryLevel", queryLevel);
        purgedProperties.put("connectTimeout", PurgeUtil.getNumericValue(connectTimeout));
        purgedProperties.put("responseTimeout", PurgeUtil.getNumericValue(responseTimeout));
        return purgedProperties;
    }
}
