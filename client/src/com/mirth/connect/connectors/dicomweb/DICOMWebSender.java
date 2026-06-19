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
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import com.mirth.connect.client.ui.ConnectorTypeDecoration;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthIconTextField;
import com.mirth.connect.client.ui.components.MirthPasswordField;
import com.mirth.connect.client.ui.components.MirthSyntaxTextArea;
import com.mirth.connect.client.ui.components.MirthTextField;
import com.mirth.connect.client.ui.panels.connectors.ConnectorSettingsPanel;
import com.mirth.connect.connectors.dicomweb.DICOMWebDispatcherProperties.AuthType;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.model.Connector.Mode;

public class DICOMWebSender extends ConnectorSettingsPanel {

    // Connection
    private MirthIconTextField urlField;
    private JComboBox<AuthType> authTypeCombo;

    // Basic
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

    // Common
    private MirthTextField connectTimeoutField;
    private MirthTextField readTimeoutField;
    private MirthSyntaxTextArea templateArea;

    public DICOMWebSender() {
        setBackground(UIConstants.BACKGROUND_COLOR);
        setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        setLayout(new MigLayout("novisualpadding, hidemode 3, insets 0", "[grow]"));

        JPanel panel = new JPanel(new MigLayout(
                "novisualpadding, hidemode 3, insets 10, gap 4", "[right][grow]"));
        panel.setBackground(UIConstants.BACKGROUND_COLOR);
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(204, 204, 204)),
                "DICOMweb STOW-RS Settings"));

        // --- Connection ---
        urlField = new MirthIconTextField();
        urlField.setToolTipText("<html>"
                + "STOW-RS endpoint URL.<br><br>"
                + "<b>Google Cloud Healthcare:</b><br>"
                + "https://healthcare.googleapis.com/v1/projects/{project}/locations/{location}"
                + "/datasets/{dataset}/dicomStores/{store}/dicomWeb/studies<br><br>"
                + "<b>Azure Health Data Services:</b><br>"
                + "https://{workspace}-{service}.dicom.azurehealthcareapis.com/v1/studies<br><br>"
                + "<b>AWS HealthImaging:</b><br>"
                + "https://medical-imaging.{region}.amazonaws.com/datastore/{datastoreId}/dicomWeb/studies"
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

        // --- Timeouts + template ---
        connectTimeoutField = new MirthTextField();
        connectTimeoutField.setToolTipText("TCP connection timeout in milliseconds.");
        readTimeoutField = new MirthTextField();
        readTimeoutField.setToolTipText("Socket read timeout in milliseconds.");
        templateArea = new MirthSyntaxTextArea();
        templateArea.setSyntaxEditingStyle(
                org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_NONE);
        templateArea.setToolTipText("DICOM content. Default: ${DICOMMESSAGE}.");

        // Layout
        panel.add(new JLabel("URL:"));           panel.add(urlField, "growx, wrap");
        panel.add(new JLabel("Authentication:")); panel.add(authTypeCombo, "w 220, wrap");

        panel.add(usernameLabel);          panel.add(usernameField, "growx, wrap");
        panel.add(passwordLabel);          panel.add(passwordField, "growx, wrap");

        panel.add(bearerTokenLabel);       panel.add(bearerTokenField, "growx, wrap");

        panel.add(saKeyFileLabel);         panel.add(saKeyFileField, "growx, wrap");

        panel.add(azureTenantIdLabel);     panel.add(azureTenantIdField, "growx, wrap");
        panel.add(azureClientIdLabel);     panel.add(azureClientIdField, "growx, wrap");
        panel.add(azureClientSecretLabel); panel.add(azureClientSecretField, "growx, wrap");
        panel.add(azureScopeLabel);        panel.add(azureScopeField, "growx, wrap");

        panel.add(awsAccessKeyIdLabel);    panel.add(awsAccessKeyIdField, "growx, wrap");
        panel.add(awsSecretAccessKeyLabel); panel.add(awsSecretAccessKeyField, "growx, wrap");
        panel.add(awsRegionLabel);         panel.add(awsRegionField, "w 120, wrap");
        panel.add(awsServiceLabel);        panel.add(awsServiceField, "w 180, wrap");

        panel.add(new JLabel("Connect Timeout (ms):")); panel.add(connectTimeoutField, "w 80, wrap");
        panel.add(new JLabel("Read Timeout (ms):"));    panel.add(readTimeoutField, "w 80, wrap");
        panel.add(new JLabel("DICOM Template:"), "aligny top");
        panel.add(templateArea, "growx, h 80, wrap");

        add(panel, "growx, span");
        updateAuthVisibility();
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

    private void setVisible(JLabel label, javax.swing.JComponent field, boolean visible) {
        label.setVisible(visible);
        field.setVisible(visible);
    }

    @Override
    public String getConnectorName() { return new DICOMWebDispatcherProperties().getName(); }

    @Override
    public ConnectorProperties getProperties() {
        DICOMWebDispatcherProperties p = new DICOMWebDispatcherProperties();
        p.setUrl(urlField.getText());
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
        p.setConnectTimeout(connectTimeoutField.getText());
        p.setReadTimeout(readTimeoutField.getText());
        p.setTemplate(templateArea.getText());
        return p;
    }

    @Override
    public void setProperties(ConnectorProperties properties) {
        DICOMWebDispatcherProperties p = (DICOMWebDispatcherProperties) properties;
        urlField.setText(p.getUrl());
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
        connectTimeoutField.setText(p.getConnectTimeout());
        readTimeoutField.setText(p.getReadTimeout());
        templateArea.setText(p.getTemplate());
        updateAuthVisibility();
    }

    @Override
    public ConnectorProperties getDefaults() { return new DICOMWebDispatcherProperties(); }

    @Override
    public boolean checkProperties(ConnectorProperties properties, boolean highlight) {
        DICOMWebDispatcherProperties p = (DICOMWebDispatcherProperties) properties;
        boolean valid = true;

        if (p.getUrl().isEmpty()) {
            if (highlight) urlField.setBackground(UIConstants.INVALID_COLOR);
            valid = false;
        }
        if (p.getAuthType() == AuthType.GOOGLE_SERVICE_ACCOUNT && p.getGoogleServiceAccountKeyFile().isEmpty()) {
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
        urlField.setBackground(UIConstants.BACKGROUND_COLOR);
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
