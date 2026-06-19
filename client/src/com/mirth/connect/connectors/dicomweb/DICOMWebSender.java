/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dicomweb;

import javax.swing.BorderFactory;
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
import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.model.Connector.Mode;

public class DICOMWebSender extends ConnectorSettingsPanel {

    private MirthIconTextField urlField;
    private MirthTextField usernameField;
    private MirthPasswordField passwordField;
    private MirthTextField bearerTokenField;
    private MirthTextField connectTimeoutField;
    private MirthTextField readTimeoutField;
    private MirthSyntaxTextArea templateArea;

    public DICOMWebSender() {
        setBackground(UIConstants.BACKGROUND_COLOR);
        setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        setLayout(new MigLayout("novisualpadding, hidemode 3, insets 0", "[right][grow]"));

        JPanel container = new JPanel(new MigLayout("novisualpadding, hidemode 3, insets 10 10 10 10, gap 4", "[right][grow]"));
        container.setBackground(UIConstants.BACKGROUND_COLOR);
        container.setBorder(BorderFactory.createTitledBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(204, 204, 204)), "DICOMweb STOW-RS Settings"));

        urlField = new MirthIconTextField();
        urlField.setToolTipText("The STOW-RS endpoint URL (e.g. http://host:8080/wado/rs/studies).");

        usernameField = new MirthTextField();
        usernameField.setToolTipText("HTTP Basic Auth username. Leave empty to skip Basic Auth.");

        passwordField = new MirthPasswordField();
        passwordField.setToolTipText("HTTP Basic Auth password.");

        bearerTokenField = new MirthTextField();
        bearerTokenField.setToolTipText("Bearer token for Authorization header. Takes precedence over Basic Auth when set.");

        connectTimeoutField = new MirthTextField();
        connectTimeoutField.setToolTipText("TCP connection timeout in milliseconds.");

        readTimeoutField = new MirthTextField();
        readTimeoutField.setToolTipText("Socket read timeout in milliseconds.");

        templateArea = new MirthSyntaxTextArea();
        templateArea.setSyntaxEditingStyle(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_NONE);
        templateArea.setToolTipText("DICOM content template. Use ${DICOMMESSAGE} to send the current DICOM message.");

        container.add(new JLabel("URL:"), "right");
        container.add(urlField, "growx, wrap");

        container.add(new JLabel("Username:"), "right");
        container.add(usernameField, "growx, wrap");

        container.add(new JLabel("Password:"), "right");
        container.add(passwordField, "growx, wrap");

        container.add(new JLabel("Bearer Token:"), "right");
        container.add(bearerTokenField, "growx, wrap");

        container.add(new JLabel("Connect Timeout (ms):"), "right");
        container.add(connectTimeoutField, "w 80, wrap");

        container.add(new JLabel("Read Timeout (ms):"), "right");
        container.add(readTimeoutField, "w 80, wrap");

        container.add(new JLabel("DICOM Template:"), "right, aligny top");
        container.add(templateArea, "growx, h 80, wrap");

        add(container, "growx, span");
    }

    @Override
    public String getConnectorName() {
        return new DICOMWebDispatcherProperties().getName();
    }

    @Override
    public ConnectorProperties getProperties() {
        DICOMWebDispatcherProperties props = new DICOMWebDispatcherProperties();
        props.setUrl(urlField.getText());
        props.setUsername(usernameField.getText());
        props.setPassword(new String(passwordField.getPassword()));
        props.setBearerToken(bearerTokenField.getText());
        props.setConnectTimeout(connectTimeoutField.getText());
        props.setReadTimeout(readTimeoutField.getText());
        props.setTemplate(templateArea.getText());
        return props;
    }

    @Override
    public void setProperties(ConnectorProperties properties) {
        DICOMWebDispatcherProperties props = (DICOMWebDispatcherProperties) properties;
        urlField.setText(props.getUrl());
        usernameField.setText(props.getUsername());
        passwordField.setText(props.getPassword());
        bearerTokenField.setText(props.getBearerToken());
        connectTimeoutField.setText(props.getConnectTimeout());
        readTimeoutField.setText(props.getReadTimeout());
        templateArea.setText(props.getTemplate());
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
            if (highlight) {
                urlField.setBackground(UIConstants.INVALID_COLOR);
            }
            valid = false;
        }

        return valid;
    }

    @Override
    public void resetInvalidProperties() {
        urlField.setBackground(UIConstants.BACKGROUND_COLOR);
    }

    @Override
    public ConnectorTypeDecoration getConnectorTypeDecoration() {
        return new ConnectorTypeDecoration(Mode.DESTINATION);
    }
}
