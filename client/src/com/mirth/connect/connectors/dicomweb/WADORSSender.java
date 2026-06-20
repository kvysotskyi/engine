/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dicomweb;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import com.mirth.connect.client.ui.ConnectorTypeDecoration;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthIconTextField;
import com.mirth.connect.client.ui.components.MirthPasswordField;
import com.mirth.connect.client.ui.components.MirthTextField;
import com.mirth.connect.client.ui.panels.connectors.ConnectorSettingsPanel;
import com.mirth.connect.connectors.dicomweb.DICOMWebDispatcherProperties.AuthType;
import com.mirth.connect.connectors.dicomweb.WADORSDispatcherProperties.RetrievalLevel;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.model.Connector.Mode;

public class WADORSSender extends ConnectorSettingsPanel {

    // Connection
    private MirthIconTextField baseUrlField;
    private JComboBox<AuthType> authTypeCombo;

    // Basic auth
    private JLabel usernameLabel;
    private MirthTextField usernameField;
    private JLabel passwordLabel;
    private MirthPasswordField passwordField;

    // Bearer
    private JLabel bearerTokenLabel;
    private MirthTextField bearerTokenField;

    // Google
    private JLabel saKeyFileLabel;
    private MirthTextField saKeyFileField;

    // Azure
    private JLabel azureTenantIdLabel;
    private MirthTextField azureTenantIdField;
    private JLabel azureClientIdLabel;
    private MirthTextField azureClientIdField;
    private JLabel azureClientSecretLabel;
    private MirthPasswordField azureClientSecretField;
    private JLabel azureScopeLabel;
    private MirthTextField azureScopeField;

    // AWS
    private JLabel awsAccessKeyIdLabel;
    private MirthTextField awsAccessKeyIdField;
    private JLabel awsSecretAccessKeyLabel;
    private MirthPasswordField awsSecretAccessKeyField;
    private JLabel awsRegionLabel;
    private MirthTextField awsRegionField;
    private JLabel awsServiceLabel;
    private MirthTextField awsServiceField;

    // Retrieval
    private JComboBox<RetrievalLevel> retrievalLevelCombo;
    private JLabel studyUidLabel;
    private MirthTextField studyUidField;
    private JLabel seriesUidLabel;
    private MirthTextField seriesUidField;
    private JLabel sopUidLabel;
    private MirthTextField sopUidField;

    // Output
    private MirthTextField outputFolderField;
    private JCheckBox overwriteExistingCheckBox;

    // Timeouts
    private MirthTextField connectTimeoutField;
    private MirthTextField readTimeoutField;

    public WADORSSender() {
        setBackground(UIConstants.BACKGROUND_COLOR);
        setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        setLayout(new MigLayout("novisualpadding, hidemode 3, insets 0", "[grow]"));

        JPanel panel = new JPanel(new MigLayout(
                "novisualpadding, hidemode 3, insets 10, gap 4", "[right][grow]"));
        panel.setBackground(UIConstants.BACKGROUND_COLOR);
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(204, 204, 204)),
                "DICOMweb WADO-RS Retrieve Settings"));

        // --- Connection ---
        baseUrlField = new MirthIconTextField();
        baseUrlField.setToolTipText("<html>"
                + "Base URL of the DICOMweb server (without /studies etc.).<br><br>"
                + "<b>Example:</b> http://localhost:8080/dcm4chee-arc/aets/DCM4CHEE/rs<br><br>"
                + "<b>Google Cloud Healthcare:</b><br>"
                + "https://healthcare.googleapis.com/v1/projects/{project}/locations/{location}"
                + "/datasets/{dataset}/dicomStores/{store}/dicomWeb<br><br>"
                + "<b>Azure Health Data Services:</b><br>"
                + "https://{workspace}-{service}.dicom.azurehealthcareapis.com/v1<br><br>"
                + "<b>AWS HealthImaging:</b><br>"
                + "https://medical-imaging.{region}.amazonaws.com/datastore/{datastoreId}/dicomWeb"
                + "</html>");

        authTypeCombo = new JComboBox<>(AuthType.values());
        authTypeCombo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) { updateAuthVisibility(); }
        });

        // --- Basic ---
        usernameLabel = new JLabel("Username:");
        usernameField = new MirthTextField();
        passwordLabel = new JLabel("Password:");
        passwordField = new MirthPasswordField();

        // --- Bearer ---
        bearerTokenLabel = new JLabel("Bearer Token:");
        bearerTokenField = new MirthTextField();

        // --- Google ---
        saKeyFileLabel = new JLabel("Service Account Key File:");
        saKeyFileField = new MirthTextField();
        saKeyFileField.setToolTipText("Absolute path to Google service account JSON key file.");

        // --- Azure ---
        azureTenantIdLabel = new JLabel("Tenant ID:");
        azureTenantIdField = new MirthTextField();
        azureClientIdLabel = new JLabel("Client ID:");
        azureClientIdField = new MirthTextField();
        azureClientSecretLabel = new JLabel("Client Secret:");
        azureClientSecretField = new MirthPasswordField();
        azureScopeLabel = new JLabel("Scope:");
        azureScopeField = new MirthTextField();

        // --- AWS ---
        awsAccessKeyIdLabel = new JLabel("Access Key ID:");
        awsAccessKeyIdField = new MirthTextField();
        awsSecretAccessKeyLabel = new JLabel("Secret Access Key:");
        awsSecretAccessKeyField = new MirthPasswordField();
        awsRegionLabel = new JLabel("Region:");
        awsRegionField = new MirthTextField();
        awsServiceLabel = new JLabel("Service:");
        awsServiceField = new MirthTextField();
        awsServiceField.setToolTipText("AWS service name (e.g. medical-imaging, execute-api).");

        // --- Retrieval ---
        retrievalLevelCombo = new JComboBox<>(RetrievalLevel.values());
        retrievalLevelCombo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) { updateRetrievalVisibility(); }
        });

        studyUidLabel = new JLabel("Study Instance UID:");
        studyUidField = new MirthTextField();
        studyUidField.setToolTipText("Study Instance UID to retrieve.");

        seriesUidLabel = new JLabel("Series Instance UID:");
        seriesUidField = new MirthTextField();
        seriesUidField.setToolTipText("Series Instance UID — required for SERIES and INSTANCE levels.");

        sopUidLabel = new JLabel("SOP Instance UID:");
        sopUidField = new MirthTextField();
        sopUidField.setToolTipText("SOP Instance UID — required for INSTANCE level.");

        // --- Output ---
        outputFolderField = new MirthTextField();
        outputFolderField.setToolTipText("<html>Local folder where retrieved DICOM files will be saved.<br>"
                + "Each instance is saved as {SOPInstanceUID}.dcm.<br>"
                + "Supports template variables, e.g. /dicom/${CHANNEL_ID}/${message.id}</html>");

        overwriteExistingCheckBox = new JCheckBox();
        overwriteExistingCheckBox.setBackground(UIConstants.BACKGROUND_COLOR);
        overwriteExistingCheckBox.setToolTipText(
                "If checked, overwrite existing files with the same SOP Instance UID.");

        // --- Timeouts ---
        connectTimeoutField = new MirthTextField();
        connectTimeoutField.setToolTipText("TCP connection timeout in milliseconds.");
        readTimeoutField = new MirthTextField();
        readTimeoutField.setToolTipText(
                "Socket read timeout in milliseconds. Use a larger value for study-level retrieval.");

        // --- Layout ---
        panel.add(new JLabel("Base URL:"));           panel.add(baseUrlField, "growx, wrap");
        panel.add(new JLabel("Authentication:"));     panel.add(authTypeCombo, "w 220, wrap");

        panel.add(usernameLabel);                     panel.add(usernameField, "growx, wrap");
        panel.add(passwordLabel);                     panel.add(passwordField, "growx, wrap");

        panel.add(bearerTokenLabel);                  panel.add(bearerTokenField, "growx, wrap");

        panel.add(saKeyFileLabel);                    panel.add(saKeyFileField, "growx, wrap");

        panel.add(azureTenantIdLabel);                panel.add(azureTenantIdField, "growx, wrap");
        panel.add(azureClientIdLabel);                panel.add(azureClientIdField, "growx, wrap");
        panel.add(azureClientSecretLabel);            panel.add(azureClientSecretField, "growx, wrap");
        panel.add(azureScopeLabel);                   panel.add(azureScopeField, "growx, wrap");

        panel.add(awsAccessKeyIdLabel);               panel.add(awsAccessKeyIdField, "growx, wrap");
        panel.add(awsSecretAccessKeyLabel);           panel.add(awsSecretAccessKeyField, "growx, wrap");
        panel.add(awsRegionLabel);                    panel.add(awsRegionField, "w 120, wrap");
        panel.add(awsServiceLabel);                   panel.add(awsServiceField, "w 180, wrap");

        panel.add(new JLabel("Retrieval Level:"));    panel.add(retrievalLevelCombo, "w 120, wrap");
        panel.add(studyUidLabel);                     panel.add(studyUidField, "growx, wrap");
        panel.add(seriesUidLabel);                    panel.add(seriesUidField, "growx, wrap");
        panel.add(sopUidLabel);                       panel.add(sopUidField, "growx, wrap");

        panel.add(new JLabel("Output Folder:"));      panel.add(outputFolderField, "growx, wrap");
        panel.add(new JLabel("Overwrite Existing:")); panel.add(overwriteExistingCheckBox, "wrap");

        panel.add(new JLabel("Connect Timeout (ms):")); panel.add(connectTimeoutField, "w 80, wrap");
        panel.add(new JLabel("Read Timeout (ms):"));    panel.add(readTimeoutField, "w 80, wrap");

        add(panel, "growx, span");
        updateAuthVisibility();
        updateRetrievalVisibility();
    }

    private void updateAuthVisibility() {
        AuthType sel = (AuthType) authTypeCombo.getSelectedItem();

        boolean basic  = sel == AuthType.BASIC;
        boolean bearer = sel == AuthType.BEARER;
        boolean google = sel == AuthType.GOOGLE_SERVICE_ACCOUNT;
        boolean azure  = sel == AuthType.AZURE_SERVICE_PRINCIPAL;
        boolean aws    = sel == AuthType.AWS_SIG_V4;

        setVisible(usernameLabel, usernameField, basic);
        setVisible(passwordLabel, passwordField, basic);
        setVisible(bearerTokenLabel, bearerTokenField, bearer);
        setVisible(saKeyFileLabel, saKeyFileField, google);
        setVisible(azureTenantIdLabel, azureTenantIdField, azure);
        setVisible(azureClientIdLabel, azureClientIdField, azure);
        setVisible(azureClientSecretLabel, azureClientSecretField, azure);
        setVisible(azureScopeLabel, azureScopeField, azure);
        setVisible(awsAccessKeyIdLabel, awsAccessKeyIdField, aws);
        setVisible(awsSecretAccessKeyLabel, awsSecretAccessKeyField, aws);
        setVisible(awsRegionLabel, awsRegionField, aws);
        setVisible(awsServiceLabel, awsServiceField, aws);
    }

    private void updateRetrievalVisibility() {
        RetrievalLevel level = (RetrievalLevel) retrievalLevelCombo.getSelectedItem();
        boolean needsSeries = level == RetrievalLevel.SERIES || level == RetrievalLevel.INSTANCE;
        boolean needsSop    = level == RetrievalLevel.INSTANCE;

        seriesUidLabel.setVisible(needsSeries);
        seriesUidField.setVisible(needsSeries);
        sopUidLabel.setVisible(needsSop);
        sopUidField.setVisible(needsSop);
    }

    private void setVisible(JLabel label, javax.swing.JComponent field, boolean visible) {
        label.setVisible(visible);
        field.setVisible(visible);
    }

    @Override
    public String getConnectorName() { return new WADORSDispatcherProperties().getName(); }

    @Override
    public ConnectorProperties getProperties() {
        WADORSDispatcherProperties p = new WADORSDispatcherProperties();
        p.setBaseUrl(baseUrlField.getText());
        p.setAuthType((AuthType) authTypeCombo.getSelectedItem());
        p.setUsername(usernameField.getText());
        p.setPassword(new String(passwordField.getPassword()));
        p.setBearerToken(bearerTokenField.getText());
        p.setGoogleServiceAccountKeyFile(saKeyFileField.getText());
        p.setAzureTenantId(azureTenantIdField.getText());
        p.setAzureClientId(azureClientIdField.getText());
        p.setAzureClientSecret(new String(azureClientSecretField.getPassword()));
        p.setAzureScope(azureScopeField.getText());
        p.setAwsAccessKeyId(awsAccessKeyIdField.getText());
        p.setAwsSecretAccessKey(new String(awsSecretAccessKeyField.getPassword()));
        p.setAwsRegion(awsRegionField.getText());
        p.setAwsService(awsServiceField.getText());
        p.setRetrievalLevel((RetrievalLevel) retrievalLevelCombo.getSelectedItem());
        p.setStudyInstanceUid(studyUidField.getText());
        p.setSeriesInstanceUid(seriesUidField.getText());
        p.setSopInstanceUid(sopUidField.getText());
        p.setOutputFolder(outputFolderField.getText());
        p.setOverwriteExisting(overwriteExistingCheckBox.isSelected());
        p.setConnectTimeout(connectTimeoutField.getText());
        p.setReadTimeout(readTimeoutField.getText());
        return p;
    }

    @Override
    public void setProperties(ConnectorProperties properties) {
        WADORSDispatcherProperties p = (WADORSDispatcherProperties) properties;
        baseUrlField.setText(p.getBaseUrl());
        authTypeCombo.setSelectedItem(p.getAuthType());
        usernameField.setText(p.getUsername());
        passwordField.setText(p.getPassword());
        bearerTokenField.setText(p.getBearerToken());
        saKeyFileField.setText(p.getGoogleServiceAccountKeyFile());
        azureTenantIdField.setText(p.getAzureTenantId());
        azureClientIdField.setText(p.getAzureClientId());
        azureClientSecretField.setText(p.getAzureClientSecret());
        azureScopeField.setText(p.getAzureScope());
        awsAccessKeyIdField.setText(p.getAwsAccessKeyId());
        awsSecretAccessKeyField.setText(p.getAwsSecretAccessKey());
        awsRegionField.setText(p.getAwsRegion());
        awsServiceField.setText(p.getAwsService());
        retrievalLevelCombo.setSelectedItem(p.getRetrievalLevel());
        studyUidField.setText(p.getStudyInstanceUid());
        seriesUidField.setText(p.getSeriesInstanceUid());
        sopUidField.setText(p.getSopInstanceUid());
        outputFolderField.setText(p.getOutputFolder());
        overwriteExistingCheckBox.setSelected(p.isOverwriteExisting());
        connectTimeoutField.setText(p.getConnectTimeout());
        readTimeoutField.setText(p.getReadTimeout());
        updateAuthVisibility();
        updateRetrievalVisibility();
    }

    @Override
    public ConnectorProperties getDefaults() { return new WADORSDispatcherProperties(); }

    @Override
    public boolean checkProperties(ConnectorProperties properties, boolean highlight) {
        WADORSDispatcherProperties p = (WADORSDispatcherProperties) properties;
        boolean valid = true;

        if (p.getBaseUrl().isEmpty()) {
            if (highlight) baseUrlField.setBackground(UIConstants.INVALID_COLOR);
            valid = false;
        }
        if (p.getStudyInstanceUid().isEmpty()) {
            if (highlight) studyUidField.setBackground(UIConstants.INVALID_COLOR);
            valid = false;
        }
        if (p.getRetrievalLevel() == RetrievalLevel.SERIES
                || p.getRetrievalLevel() == RetrievalLevel.INSTANCE) {
            if (p.getSeriesInstanceUid().isEmpty()) {
                if (highlight) seriesUidField.setBackground(UIConstants.INVALID_COLOR);
                valid = false;
            }
        }
        if (p.getRetrievalLevel() == RetrievalLevel.INSTANCE) {
            if (p.getSopInstanceUid().isEmpty()) {
                if (highlight) sopUidField.setBackground(UIConstants.INVALID_COLOR);
                valid = false;
            }
        }
        if (p.getOutputFolder().isEmpty()) {
            if (highlight) outputFolderField.setBackground(UIConstants.INVALID_COLOR);
            valid = false;
        }
        if (p.getAuthType() == AuthType.GOOGLE_SERVICE_ACCOUNT
                && p.getGoogleServiceAccountKeyFile().isEmpty()) {
            if (highlight) saKeyFileField.setBackground(UIConstants.INVALID_COLOR);
            valid = false;
        }
        if (p.getAuthType() == AuthType.AZURE_SERVICE_PRINCIPAL) {
            if (p.getAzureTenantId().isEmpty()) {
                if (highlight) azureTenantIdField.setBackground(UIConstants.INVALID_COLOR);
                valid = false;
            }
            if (p.getAzureClientId().isEmpty()) {
                if (highlight) azureClientIdField.setBackground(UIConstants.INVALID_COLOR);
                valid = false;
            }
        }
        if (p.getAuthType() == AuthType.AWS_SIG_V4) {
            if (p.getAwsAccessKeyId().isEmpty()) {
                if (highlight) awsAccessKeyIdField.setBackground(UIConstants.INVALID_COLOR);
                valid = false;
            }
            if (p.getAwsRegion().isEmpty()) {
                if (highlight) awsRegionField.setBackground(UIConstants.INVALID_COLOR);
                valid = false;
            }
        }
        return valid;
    }

    @Override
    public void resetInvalidProperties() {
        baseUrlField.setBackground(UIConstants.BACKGROUND_COLOR);
        studyUidField.setBackground(UIConstants.BACKGROUND_COLOR);
        seriesUidField.setBackground(UIConstants.BACKGROUND_COLOR);
        sopUidField.setBackground(UIConstants.BACKGROUND_COLOR);
        outputFolderField.setBackground(UIConstants.BACKGROUND_COLOR);
        saKeyFileField.setBackground(UIConstants.BACKGROUND_COLOR);
        azureTenantIdField.setBackground(UIConstants.BACKGROUND_COLOR);
        azureClientIdField.setBackground(UIConstants.BACKGROUND_COLOR);
        awsAccessKeyIdField.setBackground(UIConstants.BACKGROUND_COLOR);
        awsRegionField.setBackground(UIConstants.BACKGROUND_COLOR);
    }

    @Override
    public ConnectorTypeDecoration getConnectorTypeDecoration() {
        return new ConnectorTypeDecoration(Mode.DESTINATION);
    }
}
