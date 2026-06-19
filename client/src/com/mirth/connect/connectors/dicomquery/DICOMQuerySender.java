/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dicomquery;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import com.mirth.connect.client.ui.ConnectorTypeDecoration;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthTextField;
import com.mirth.connect.client.ui.panels.connectors.ConnectorSettingsPanel;
import com.mirth.connect.connectors.dicomquery.DICOMQueryDispatcherProperties.QueryLevel;
import com.mirth.connect.connectors.dicomquery.DICOMQueryDispatcherProperties.QueryModel;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.model.Connector.Mode;

public class DICOMQuerySender extends ConnectorSettingsPanel {

    // Network
    private MirthTextField hostField;
    private MirthTextField portField;
    private MirthTextField calledAetField;
    private MirthTextField callingAetField;

    // Query
    private JComboBox<QueryModel> queryModelCombo;
    private JComboBox<QueryLevel> queryLevelCombo;
    private JLabel queryLevelLabel;

    // Filter keys
    private MirthTextField patientIdField;
    private MirthTextField patientNameField;
    private MirthTextField studyDateField;
    private MirthTextField modalityField;
    private MirthTextField accessionField;
    private MirthTextField studyUidField;
    private MirthTextField seriesUidField;
    private JLabel seriesUidLabel;
    private MirthTextField sopUidField;
    private JLabel sopUidLabel;

    // Worklist-only
    private JLabel scheduledDateLabel;
    private MirthTextField scheduledDateField;
    private JLabel scheduledAetLabel;
    private MirthTextField scheduledAetField;

    // Timeouts
    private MirthTextField connectTimeoutField;
    private MirthTextField responseTimeoutField;

    public DICOMQuerySender() {
        setBackground(UIConstants.BACKGROUND_COLOR);
        setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        setLayout(new MigLayout("novisualpadding, hidemode 3, insets 0", "[grow]"));

        JPanel panel = new JPanel(new MigLayout(
                "novisualpadding, hidemode 3, insets 10, gap 4", "[right][grow]"));
        panel.setBackground(UIConstants.BACKGROUND_COLOR);
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(204, 204, 204)),
                "DICOM C-FIND SCU Settings"));

        // Network
        hostField = new MirthTextField();
        portField = new MirthTextField();
        portField.setToolTipText("DICOM port (default: 104).");
        calledAetField = new MirthTextField();
        calledAetField.setToolTipText("AE Title of the remote DICOM node (C-FIND SCP).");
        callingAetField = new MirthTextField();
        callingAetField.setToolTipText("Local AE Title used by this SCU.");

        // Query model / level
        queryModelCombo = new JComboBox<>(QueryModel.values());
        queryModelCombo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) { updateVisibility(); }
        });

        queryLevelLabel = new JLabel("Query Level:");
        queryLevelCombo = new JComboBox<>(QueryLevel.values());

        // Filter keys
        patientIdField     = new MirthTextField();
        patientNameField   = new MirthTextField();
        patientNameField.setToolTipText("Supports wildcards: * and ?");
        studyDateField     = new MirthTextField();
        studyDateField.setToolTipText("YYYYMMDD or range YYYYMMDD-YYYYMMDD");
        modalityField      = new MirthTextField();
        accessionField     = new MirthTextField();
        studyUidField      = new MirthTextField();
        seriesUidLabel     = new JLabel("Series UID:");
        seriesUidField     = new MirthTextField();
        sopUidLabel        = new JLabel("SOP Instance UID:");
        sopUidField        = new MirthTextField();

        // Worklist
        scheduledDateLabel = new JLabel("Scheduled Date:");
        scheduledDateField = new MirthTextField();
        scheduledDateField.setToolTipText("YYYYMMDD or range YYYYMMDD-YYYYMMDD");
        scheduledAetLabel  = new JLabel("Scheduled Station AET:");
        scheduledAetField  = new MirthTextField();

        // Timeouts
        connectTimeoutField  = new MirthTextField();
        connectTimeoutField.setToolTipText("TCP connection timeout in milliseconds.");
        responseTimeoutField = new MirthTextField();
        responseTimeoutField.setToolTipText("DIMSE response timeout in milliseconds.");

        // Layout
        panel.add(new JLabel("Host:"));               panel.add(hostField, "growx, wrap");
        panel.add(new JLabel("Port:"));               panel.add(portField, "w 80, wrap");
        panel.add(new JLabel("Called AET:"));         panel.add(calledAetField, "w 160, wrap");
        panel.add(new JLabel("Calling AET:"));        panel.add(callingAetField, "w 160, wrap");
        panel.add(new JLabel("Query Model:"));        panel.add(queryModelCombo, "w 220, wrap");
        panel.add(queryLevelLabel);                   panel.add(queryLevelCombo, "w 180, wrap");

        panel.add(new JLabel("Patient ID:"));         panel.add(patientIdField, "growx, wrap");
        panel.add(new JLabel("Patient Name:"));       panel.add(patientNameField, "growx, wrap");
        panel.add(new JLabel("Study Date:"));         panel.add(studyDateField, "growx, wrap");
        panel.add(new JLabel("Modality:"));           panel.add(modalityField, "growx, wrap");
        panel.add(new JLabel("Accession Number:"));   panel.add(accessionField, "growx, wrap");
        panel.add(new JLabel("Study Instance UID:")); panel.add(studyUidField, "growx, wrap");
        panel.add(seriesUidLabel);                    panel.add(seriesUidField, "growx, wrap");
        panel.add(sopUidLabel);                       panel.add(sopUidField, "growx, wrap");

        panel.add(scheduledDateLabel);                panel.add(scheduledDateField, "growx, wrap");
        panel.add(scheduledAetLabel);                 panel.add(scheduledAetField, "growx, wrap");

        panel.add(new JLabel("Connect Timeout (ms):")); panel.add(connectTimeoutField, "w 80, wrap");
        panel.add(new JLabel("Response Timeout (ms):")); panel.add(responseTimeoutField, "w 80, wrap");

        add(panel, "growx, span");
        updateVisibility();
    }

    private void updateVisibility() {
        QueryModel model = (QueryModel) queryModelCombo.getSelectedItem();
        boolean worklist = model == QueryModel.WORKLIST;

        queryLevelLabel.setVisible(!worklist);
        queryLevelCombo.setVisible(!worklist);

        seriesUidLabel.setVisible(!worklist);
        seriesUidField.setVisible(!worklist);
        sopUidLabel.setVisible(!worklist);
        sopUidField.setVisible(!worklist);

        scheduledDateLabel.setVisible(worklist);
        scheduledDateField.setVisible(worklist);
        scheduledAetLabel.setVisible(worklist);
        scheduledAetField.setVisible(worklist);
    }

    @Override
    public String getConnectorName() { return new DICOMQueryDispatcherProperties().getName(); }

    @Override
    public ConnectorProperties getProperties() {
        DICOMQueryDispatcherProperties p = new DICOMQueryDispatcherProperties();
        p.setHost(hostField.getText());
        p.setPort(portField.getText());
        p.setCalledAet(calledAetField.getText());
        p.setCallingAet(callingAetField.getText());
        p.setQueryModel((QueryModel) queryModelCombo.getSelectedItem());
        p.setQueryLevel((QueryLevel) queryLevelCombo.getSelectedItem());
        p.setPatientId(patientIdField.getText());
        p.setPatientName(patientNameField.getText());
        p.setStudyDate(studyDateField.getText());
        p.setModality(modalityField.getText());
        p.setAccessionNumber(accessionField.getText());
        p.setStudyInstanceUid(studyUidField.getText());
        p.setSeriesInstanceUid(seriesUidField.getText());
        p.setSopInstanceUid(sopUidField.getText());
        p.setScheduledDate(scheduledDateField.getText());
        p.setScheduledAet(scheduledAetField.getText());
        p.setConnectTimeout(connectTimeoutField.getText());
        p.setResponseTimeout(responseTimeoutField.getText());
        return p;
    }

    @Override
    public void setProperties(ConnectorProperties properties) {
        DICOMQueryDispatcherProperties p = (DICOMQueryDispatcherProperties) properties;
        hostField.setText(p.getHost());
        portField.setText(p.getPort());
        calledAetField.setText(p.getCalledAet());
        callingAetField.setText(p.getCallingAet());
        queryModelCombo.setSelectedItem(p.getQueryModel());
        queryLevelCombo.setSelectedItem(p.getQueryLevel());
        patientIdField.setText(p.getPatientId());
        patientNameField.setText(p.getPatientName());
        studyDateField.setText(p.getStudyDate());
        modalityField.setText(p.getModality());
        accessionField.setText(p.getAccessionNumber());
        studyUidField.setText(p.getStudyInstanceUid());
        seriesUidField.setText(p.getSeriesInstanceUid());
        sopUidField.setText(p.getSopInstanceUid());
        scheduledDateField.setText(p.getScheduledDate());
        scheduledAetField.setText(p.getScheduledAet());
        connectTimeoutField.setText(p.getConnectTimeout());
        responseTimeoutField.setText(p.getResponseTimeout());
        updateVisibility();
    }

    @Override
    public ConnectorProperties getDefaults() { return new DICOMQueryDispatcherProperties(); }

    @Override
    public boolean checkProperties(ConnectorProperties properties, boolean highlight) {
        DICOMQueryDispatcherProperties p = (DICOMQueryDispatcherProperties) properties;
        boolean valid = true;
        if (p.getHost().isEmpty()) {
            if (highlight) hostField.setBackground(UIConstants.INVALID_COLOR);
            valid = false;
        }
        if (p.getPort().isEmpty()) {
            if (highlight) portField.setBackground(UIConstants.INVALID_COLOR);
            valid = false;
        }
        if (p.getCalledAet().isEmpty()) {
            if (highlight) calledAetField.setBackground(UIConstants.INVALID_COLOR);
            valid = false;
        }
        return valid;
    }

    @Override
    public void resetInvalidProperties() {
        hostField.setBackground(UIConstants.BACKGROUND_COLOR);
        portField.setBackground(UIConstants.BACKGROUND_COLOR);
        calledAetField.setBackground(UIConstants.BACKGROUND_COLOR);
    }

    @Override
    public ConnectorTypeDecoration getConnectorTypeDecoration() {
        return new ConnectorTypeDecoration(Mode.DESTINATION);
    }
}
