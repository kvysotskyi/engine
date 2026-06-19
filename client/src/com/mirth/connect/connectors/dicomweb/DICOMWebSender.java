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

    private MirthIconTextField urlField;
    private JComboBox<AuthType> authTypeCombo;

    // Basic auth fields
    private JLabel usernameLabel;
    private MirthTextField usernameField;
    private JLabel passwordLabel;
    private MirthPasswordField passwordField;

    // Bearer token field
    private JLabel bearerTokenLabel;
    private MirthTextField bearerTokenField;

    // Google service account field
    private JLabel saKeyFileLabel;
    private MirthTextField saKeyFileField;

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

        // URL
        urlField = new MirthIconTextField();
        urlField.setToolTipText("<html>STOW-RS endpoint URL.<br>"
                + "For Google Cloud Healthcare:<br>"
                + "https://healthcare.googleapis.com/v1/projects/{project}/locations/{location}"
                + "/datasets/{dataset}/dicomStores/{store}/dicomWeb/studies</html>");

        // Auth type selector
        authTypeCombo = new JComboBox<>(AuthType.values());
        authTypeCombo.setToolTipText("Authentication method for the STOW-RS request.");
        authTypeCombo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateAuthVisibility();
            }
        });

        // Basic auth
        usernameLabel = new JLabel("Username:");
        usernameField = new MirthTextField();
        passwordLabel = new JLabel("Password:");
        passwordField = new MirthPasswordField();

        // Bearer token
        bearerTokenLabel = new JLabel("Bearer Token:");
        bearerTokenField = new MirthTextField();

        // Google service account
        saKeyFileLabel = new JLabel("Service Account Key File:");
        saKeyFileField = new MirthTextField();
        saKeyFileField.setToolTipText("Absolute path to the Google service account JSON key file.");

        // Timeouts
        connectTimeoutField = new MirthTextField();
        connectTimeoutField.setToolTipText("TCP connection timeout in milliseconds.");
        readTimeoutField = new MirthTextField();
        readTimeoutField.setToolTipText("Socket read timeout in milliseconds.");

        // Template
        templateArea = new MirthSyntaxTextArea();
        templateArea.setSyntaxEditingStyle(
                org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_NONE);
        templateArea.setToolTipText("DICOM content. Use ${DICOMMESSAGE} to send the current message.");

        panel.add(new JLabel("URL:"));
        panel.add(urlField, "growx, wrap");

        panel.add(new JLabel("Authentication:"));
        panel.add(authTypeCombo, "w 200, wrap");

        panel.add(usernameLabel);
        panel.add(usernameField, "growx, wrap");
        panel.add(passwordLabel);
        panel.add(passwordField, "growx, wrap");

        panel.add(bearerTokenLabel);
        panel.add(bearerTokenField, "growx, wrap");

        panel.add(saKeyFileLabel);
        panel.add(saKeyFileField, "growx, wrap");

        panel.add(new JLabel("Connect Timeout (ms):"));
        panel.add(connectTimeoutField, "w 80, wrap");
        panel.add(new JLabel("Read Timeout (ms):"));
        panel.add(readTimeoutField, "w 80, wrap");

        panel.add(new JLabel("DICOM Template:"), "aligny top");
        panel.add(templateArea, "growx, h 80, wrap");

        add(panel, "growx, span");

        updateAuthVisibility();
    }

    private void updateAuthVisibility() {
        AuthType selected = (AuthType) authTypeCombo.getSelectedItem();

        boolean isBasic   = selected == AuthType.BASIC;
        boolean isBearer  = selected == AuthType.BEARER;
        boolean isGoogle  = selected == AuthType.GOOGLE_SERVICE_ACCOUNT;

        usernameLabel.setVisible(isBasic);
        usernameField.setVisible(isBasic);
        passwordLabel.setVisible(isBasic);
        passwordField.setVisible(isBasic);

        bearerTokenLabel.setVisible(isBearer);
        bearerTokenField.setVisible(isBearer);

        saKeyFileLabel.setVisible(isGoogle);
        saKeyFileField.setVisible(isGoogle);
    }

    @Override
    public String getConnectorName() {
        return new DICOMWebDispatcherProperties().getName();
    }

    @Override
    public ConnectorProperties getProperties() {
        DICOMWebDispatcherProperties props = new DICOMWebDispatcherProperties();
        props.setUrl(urlField.getText());
        props.setAuthType((AuthType) authTypeCombo.getSelectedItem());
        props.setUsername(usernameField.getText());
        props.setPassword(new String(passwordField.getPassword()));
        props.setBearerToken(bearerTokenField.getText());
        props.setGoogleServiceAccountKeyFile(saKeyFileField.getText());
        props.setConnectTimeout(connectTimeoutField.getText());
        props.setReadTimeout(readTimeoutField.getText());
        props.setTemplate(templateArea.getText());
        return props;
    }

    @Override
    public void setProperties(ConnectorProperties properties) {
        DICOMWebDispatcherProperties props = (DICOMWebDispatcherProperties) properties;
        urlField.setText(props.getUrl());
        authTypeCombo.setSelectedItem(props.getAuthType());
        usernameField.setText(props.getUsername());
        passwordField.setText(props.getPassword());
        bearerTokenField.setText(props.getBearerToken());
        saKeyFileField.setText(props.getGoogleServiceAccountKeyFile());
        connectTimeoutField.setText(props.getConnectTimeout());
        readTimeoutField.setText(props.getReadTimeout());
        templateArea.setText(props.getTemplate());
        updateAuthVisibility();
    }

    @Override
    public ConnectorProperties getDefaults() {
        return new DICOMWebDispatcherProperties();
    }

    @Override
    public boolean checkProperties(ConnectorProperties properties, boolean highlight) {
        DICOMWebDispatcherProperties props = (DICOMWebDispatcherProperties) properties;
        boolean valid = true;

        if (props.getUrl().isEmpty()) {
            if (highlight) urlField.setBackground(UIConstants.INVALID_COLOR);
            valid = false;
        }

        if (props.getAuthType() == AuthType.GOOGLE_SERVICE_ACCOUNT
                && props.getGoogleServiceAccountKeyFile().isEmpty()) {
            if (highlight) saKeyFileField.setBackground(UIConstants.INVALID_COLOR);
            valid = false;
        }

        return valid;
    }

    @Override
    public void resetInvalidProperties() {
        urlField.setBackground(UIConstants.BACKGROUND_COLOR);
        saKeyFileField.setBackground(UIConstants.BACKGROUND_COLOR);
    }

    @Override
    public ConnectorTypeDecoration getConnectorTypeDecoration() {
        return new ConnectorTypeDecoration(Mode.DESTINATION);
    }
}
