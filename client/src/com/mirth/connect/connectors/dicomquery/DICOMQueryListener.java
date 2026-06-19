/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dicomquery;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import com.mirth.connect.client.ui.ConnectorTypeDecoration;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthTextField;
import com.mirth.connect.client.ui.panels.connectors.ConnectorSettingsPanel;
import com.mirth.connect.connectors.dicomquery.DICOMQueryReceiverProperties.AcceptedModel;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.model.Connector.Mode;

public class DICOMQueryListener extends ConnectorSettingsPanel {

    private MirthTextField hostField;
    private MirthTextField portField;
    private MirthTextField aetField;
    private JComboBox<AcceptedModel> acceptedModelCombo;
    private MirthTextField idleTimeoutField;
    private MirthTextField requestTimeoutField;
    private MirthTextField releaseTimeoutField;

    public DICOMQueryListener() {
        setBackground(UIConstants.BACKGROUND_COLOR);
        setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        setLayout(new MigLayout("novisualpadding, hidemode 3, insets 0", "[grow]"));

        JPanel panel = new JPanel(new MigLayout(
                "novisualpadding, hidemode 3, insets 10, gap 4", "[right][grow]"));
        panel.setBackground(UIConstants.BACKGROUND_COLOR);
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(204, 204, 204)),
                "DICOM C-FIND SCP Settings"));

        hostField = new MirthTextField();
        hostField.setToolTipText("Bind address (leave blank to listen on all interfaces).");
        portField = new MirthTextField();
        portField.setToolTipText("DICOM listen port (default: 104).");
        aetField = new MirthTextField();
        aetField.setToolTipText("Local AE Title this SCP presents to incoming SCUs.");
        acceptedModelCombo = new JComboBox<>(AcceptedModel.values());
        acceptedModelCombo.setToolTipText(
                "<html>Query information models accepted by this SCP.<br>"
                + "ALL accepts Study Root, Patient Root, and Worklist.</html>");
        idleTimeoutField = new MirthTextField();
        idleTimeoutField.setToolTipText("Idle association timeout in milliseconds.");
        requestTimeoutField = new MirthTextField();
        requestTimeoutField.setToolTipText("A-ASSOCIATE-RQ timeout in milliseconds.");
        releaseTimeoutField = new MirthTextField();
        releaseTimeoutField.setToolTipText("A-RELEASE-RQ timeout in milliseconds.");

        panel.add(new JLabel("Host:"));                    panel.add(hostField, "growx, wrap");
        panel.add(new JLabel("Port:"));                    panel.add(portField, "w 80, wrap");
        panel.add(new JLabel("AE Title:"));                panel.add(aetField, "w 160, wrap");
        panel.add(new JLabel("Accepted Model:"));          panel.add(acceptedModelCombo, "w 220, wrap");
        panel.add(new JLabel("Idle Timeout (ms):"));       panel.add(idleTimeoutField, "w 80, wrap");
        panel.add(new JLabel("Request Timeout (ms):"));    panel.add(requestTimeoutField, "w 80, wrap");
        panel.add(new JLabel("Release Timeout (ms):"));    panel.add(releaseTimeoutField, "w 80, wrap");

        add(panel, "growx, span");
    }

    @Override
    public String getConnectorName() { return new DICOMQueryReceiverProperties().getName(); }

    @Override
    public ConnectorProperties getProperties() {
        DICOMQueryReceiverProperties p = new DICOMQueryReceiverProperties();
        p.getListenerConnectorProperties().setHost(hostField.getText());
        p.getListenerConnectorProperties().setPort(portField.getText());
        p.setApplicationEntity(aetField.getText());
        p.setAcceptedModel((AcceptedModel) acceptedModelCombo.getSelectedItem());
        p.setIdleTimeout(idleTimeoutField.getText());
        p.setRequestTimeout(requestTimeoutField.getText());
        p.setReleaseTimeout(releaseTimeoutField.getText());
        return p;
    }

    @Override
    public void setProperties(ConnectorProperties properties) {
        DICOMQueryReceiverProperties p = (DICOMQueryReceiverProperties) properties;
        hostField.setText(p.getListenerConnectorProperties().getHost());
        portField.setText(p.getListenerConnectorProperties().getPort());
        aetField.setText(p.getApplicationEntity());
        acceptedModelCombo.setSelectedItem(p.getAcceptedModel());
        idleTimeoutField.setText(p.getIdleTimeout());
        requestTimeoutField.setText(p.getRequestTimeout());
        releaseTimeoutField.setText(p.getReleaseTimeout());
    }

    @Override
    public ConnectorProperties getDefaults() { return new DICOMQueryReceiverProperties(); }

    @Override
    public boolean checkProperties(ConnectorProperties properties, boolean highlight) {
        DICOMQueryReceiverProperties p = (DICOMQueryReceiverProperties) properties;
        boolean valid = true;
        if (p.getListenerConnectorProperties().getPort().isEmpty()) {
            if (highlight) portField.setBackground(UIConstants.INVALID_COLOR);
            valid = false;
        }
        return valid;
    }

    @Override
    public void resetInvalidProperties() {
        portField.setBackground(UIConstants.BACKGROUND_COLOR);
    }

    @Override
    public ConnectorTypeDecoration getConnectorTypeDecoration() {
        return new ConnectorTypeDecoration(Mode.SOURCE);
    }
}
