package org.dcm4che2.tool.dcmrcv;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.io.DicomOutputStream;
import org.dcm4che2.net.Association;
import org.dcm4che2.net.Device;
import org.dcm4che2.net.DicomServiceException;
import org.dcm4che2.net.NetworkConnection;
import org.dcm4che2.net.PDVInputStream;
import org.dcm4che2.net.pdu.PresentationContext;
import org.dcm4che2.net.pdu.UserIdentityRQ;

import com.mirth.connect.connectors.dimse.DICOMConfiguration;
import com.mirth.connect.donkey.model.message.RawMessage;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.server.channel.DispatchResult;
import com.mirth.connect.donkey.server.channel.SourceConnector;
import com.mirth.connect.donkey.util.Base64Util;
import com.mirth.connect.model.converters.DICOMConverter;

public class MirthDcmRcv extends DcmRcv {
    private Logger logger = LogManager.getLogger(this.getClass());
    private SourceConnector sourceConnector;
    private DICOMConfiguration dicomConfiguration;

    private String storageFolder;
    private boolean deleteAfterProcessing;

    // messageId -> file, for deleteAfterProcessing in async mode
    private final ConcurrentHashMap<Long, File> pendingFiles = new ConcurrentHashMap<>();

    public MirthDcmRcv(SourceConnector sourceConnector, DICOMConfiguration dicomConfiguration) {
        super("DCMRCV", false);
        this.sourceConnector = sourceConnector;
        this.dicomConfiguration = dicomConfiguration;
        init();
    }

    public void setStorageFolder(String storageFolder) {
        this.storageFolder = storageFolder;
    }

    public void setDeleteAfterProcessing(boolean deleteAfterProcessing) {
        this.deleteAfterProcessing = deleteAfterProcessing;
    }

    public Device getDevice() {
        return device;
    }

    public NetworkConnection getNetworkConnection() {
        return nc;
    }

    @Override
    protected NetworkConnection createNetworkConnection() {
        return dicomConfiguration.createNetworkConnection();
    }

    public boolean isFileModeEnabled() {
        return storageFolder != null && !storageFolder.isEmpty();
    }

    /**
     * Called by DICOMReceiver.finishDispatch() after pipeline completes, to delete the file
     * if deleteAfterProcessing is enabled.
     */
    public void onDispatchComplete(long messageId) {
        if (deleteAfterProcessing) {
            File file = pendingFiles.remove(messageId);
            if (file != null && file.exists()) {
                if (!file.delete()) {
                    logger.warn("Could not delete DICOM file after processing: " + file.getAbsolutePath());
                }
            }
        }
    }

    @Override
    void onCStoreRQ(Association as, int pcid, DicomObject rq, PDVInputStream dataStream, String tsuid, DicomObject rsp) throws IOException, DicomServiceException {
        if (isFileModeEnabled()) {
            onCStoreRQFileMode(as, rq, dataStream, tsuid, rsp);
        } else {
            onCStoreRQLegacy(as, pcid, rq, dataStream, tsuid, rsp);
        }
    }

    private void onCStoreRQFileMode(Association as, DicomObject rq, PDVInputStream dataStream, String tsuid, DicomObject rsp) throws IOException, DicomServiceException {
        String cuid = rq.getString(Tag.AffectedSOPClassUID);
        String iuid = rq.getString(Tag.AffectedSOPInstanceUID);

        BasicDicomObject fileMetaInformation = new BasicDicomObject();
        fileMetaInformation.initFileMetaInformation(cuid, iuid, tsuid);

        String originalThreadName = Thread.currentThread().getName();
        File dicomFile = null;
        DispatchResult dispatchResult = null;

        try {
            Thread.currentThread().setName("DICOM Receiver Thread on " + sourceConnector.getChannel().getName() + " (" + sourceConnector.getChannelId() + ") < " + originalThreadName);

            // 1. Stream directly to disk — no ByteArrayOutputStream
            File storageDir = new File(storageFolder);
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }
            dicomFile = new File(storageDir, iuid + ".dcm");
            DicomOutputStream dos = new DicomOutputStream(new BufferedOutputStream(new FileOutputStream(dicomFile)));
            try {
                dos.writeFileMetaInformation(fileMetaInformation);
                dataStream.copyTo(dos);
            } finally {
                IOUtils.closeQuietly(dos);
            }

            // 2. Parse header only — stop before PixelData, never load pixel data into memory
            byte[] headerBytes = readHeaderBytes(dicomFile);
            String rawMessage = StringUtils.newStringUsAscii(Base64Util.encodeBase64(headerBytes));

            // 3. Build source map
            Map<String, Object> sourceMap = buildSourceMap(as);
            sourceMap.put("dicomFile", dicomFile.getAbsolutePath());

            // 4. Dispatch — respondAfterProcessing=false means C-STORE-RSP is sent
            //    immediately after raw message is persisted, pipeline runs async
            try {
                dispatchResult = sourceConnector.dispatchRawMessage(new RawMessage(rawMessage, null, sourceMap));

                if (dispatchResult != null && dispatchResult.getSelectedResponse() != null
                        && dispatchResult.getSelectedResponse().getStatus() == Status.ERROR) {
                    throw new DicomServiceException(rq, org.dcm4che2.net.Status.ProcessingFailure,
                            dispatchResult.getSelectedResponse().getStatusMessage());
                }

                // Register for deferred file deletion after async processing completes
                if (deleteAfterProcessing && dispatchResult != null) {
                    pendingFiles.put(dispatchResult.getMessageId(), dicomFile);
                    dicomFile = null; // prevent deletion in finally block on success
                }
            } finally {
                sourceConnector.finishDispatch(dispatchResult);
            }
        } catch (Throwable t) {
            logger.error("Error receiving DICOM message on channel " + sourceConnector.getChannelId(), t);
            if (t instanceof DicomServiceException) {
                throw (DicomServiceException) t;
            }
            throw new DicomServiceException(rq, org.dcm4che2.net.Status.ProcessingFailure, "Error processing DICOM message: " + t.getMessage());
        } finally {
            Thread.currentThread().setName(originalThreadName);
            // Only delete file on error path (dicomFile is null on success path when deleteAfterProcessing=true)
            if (dicomFile != null && deleteAfterProcessing) {
                dicomFile.delete();
            }
        }
    }

    private void onCStoreRQLegacy(Association as, int pcid, DicomObject rq, PDVInputStream dataStream, String tsuid, DicomObject rsp) throws IOException, DicomServiceException {
        java.io.ByteArrayOutputStream baos = null;
        BufferedOutputStream bos = null;
        DicomOutputStream dos = null;

        String cuid = rq.getString(Tag.AffectedSOPClassUID);
        String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
        BasicDicomObject fileMetaInformation = new BasicDicomObject();
        fileMetaInformation.initFileMetaInformation(cuid, iuid, tsuid);

        String originalThreadName = Thread.currentThread().getName();
        try {
            Thread.currentThread().setName("DICOM Receiver Thread on " + sourceConnector.getChannel().getName() + " (" + sourceConnector.getChannelId() + ") < " + originalThreadName);

            Map<String, Object> sourceMap = buildSourceMap(as);

            baos = new java.io.ByteArrayOutputStream();
            bos = new BufferedOutputStream(baos);
            dos = new DicomOutputStream(bos);
            dos.writeFileMetaInformation(fileMetaInformation);
            dataStream.copyTo(dos);
            dos.close();

            byte[] dicomMessage = baos.toByteArray();
            bos = null;
            baos = null;

            DispatchResult dispatchResult = null;
            try {
                dispatchResult = sourceConnector.dispatchRawMessage(new RawMessage(dicomMessage, null, sourceMap));

                if (dispatchResult != null && dispatchResult.getSelectedResponse() != null
                        && dispatchResult.getSelectedResponse().getStatus() == Status.ERROR) {
                    throw new DicomServiceException(rq, org.dcm4che2.net.Status.ProcessingFailure,
                            dispatchResult.getSelectedResponse().getStatusMessage());
                }
            } finally {
                sourceConnector.finishDispatch(dispatchResult);
            }
        } catch (Throwable t) {
            logger.error("Error receiving DICOM message on channel " + sourceConnector.getChannelId(), t);
            if (t instanceof DicomServiceException) {
                throw (DicomServiceException) t;
            }
            throw new DicomServiceException(rq, org.dcm4che2.net.Status.ProcessingFailure, "Error processing DICOM message: " + t.getMessage());
        } finally {
            Thread.currentThread().setName(originalThreadName);
            IOUtils.closeQuietly(baos);
            IOUtils.closeQuietly(bos);
        }
    }

    /**
     * Reads DICOM header bytes from file, stopping before PixelData.
     * Never loads pixel data into memory.
     */
    private byte[] readHeaderBytes(File file) throws IOException {
        BasicDicomObject header = new BasicDicomObject();
        DicomInputStream dis = new DicomInputStream(new BufferedInputStream(new FileInputStream(file)));
        try {
            dis.setAllocateLimit(-1);
            dis.readDicomObject(header, Tag.PixelData);
        } finally {
            IOUtils.closeQuietly(dis);
        }
        return DICOMConverter.dicomObjectToByteArray(header);
    }

    private Map<String, Object> buildSourceMap(Association as) {
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("localApplicationEntityTitle", as.getLocalAET());
        sourceMap.put("remoteApplicationEntityTitle", as.getRemoteAET());

        if (as.getSocket() != null) {
            sourceMap.put("localAddress", as.getSocket().getLocalAddress().getHostAddress());
            sourceMap.put("localPort", as.getSocket().getLocalPort());
            if (as.getSocket().getRemoteSocketAddress() instanceof InetSocketAddress) {
                sourceMap.put("remoteAddress", ((InetSocketAddress) as.getSocket().getRemoteSocketAddress()).getAddress().getHostAddress());
                sourceMap.put("remotePort", ((InetSocketAddress) as.getSocket().getRemoteSocketAddress()).getPort());
            }
        }

        if (as.getAssociateAC() != null) {
            sourceMap.put("associateACProtocolVersion", as.getAssociateAC().getProtocolVersion());
            sourceMap.put("associateACImplClassUID", as.getAssociateAC().getImplClassUID());
            sourceMap.put("associateACImplVersionName", as.getAssociateAC().getImplVersionName());
            sourceMap.put("associateACApplicationContext", as.getAssociateAC().getApplicationContext());

            if (as.getAssociateAC().getNumberOfPresentationContexts() > 0) {
                Map<Integer, String> pcMap = new LinkedHashMap<>();
                for (PresentationContext pc : as.getAssociateAC().getPresentationContexts()) {
                    pcMap.put(pc.getPCID(), pc.toString());
                }
                sourceMap.put("associateACPresentationContexts", MapUtils.unmodifiableMap(pcMap));
            }
        }

        if (as.getAssociateRQ() != null) {
            sourceMap.put("associateRQProtocolVersion", as.getAssociateRQ().getProtocolVersion());
            sourceMap.put("associateRQImplClassUID", as.getAssociateRQ().getImplClassUID());
            sourceMap.put("associateRQImplVersionName", as.getAssociateRQ().getImplVersionName());
            sourceMap.put("associateRQApplicationContext", as.getAssociateRQ().getApplicationContext());

            if (as.getAssociateRQ().getNumberOfPresentationContexts() > 0) {
                Map<Integer, String> pcMap = new LinkedHashMap<>();
                for (PresentationContext pc : as.getAssociateRQ().getPresentationContexts()) {
                    pcMap.put(pc.getPCID(), pc.toString());
                }
                sourceMap.put("associateRQPresentationContexts", MapUtils.unmodifiableMap(pcMap));
            }

            if (as.getAssociateRQ().getUserIdentity() != null) {
                sourceMap.put("username", as.getAssociateRQ().getUserIdentity().getUsername());
                sourceMap.put("passcode", new String(as.getAssociateRQ().getUserIdentity().getPasscode()));

                int type = as.getAssociateRQ().getUserIdentity().getUserIdentityType();
                String typeString;
                switch (type) {
                    case UserIdentityRQ.USERNAME: typeString = "USERNAME"; break;
                    case UserIdentityRQ.USERNAME_PASSCODE: typeString = "USERNAME_PASSCODE"; break;
                    case UserIdentityRQ.KERBEROS: typeString = "KERBEROS"; break;
                    case UserIdentityRQ.SAML: typeString = "SAML"; break;
                    default: typeString = String.valueOf(type);
                }
                sourceMap.put("userIdentityType", typeString);
            }
        }

        sourceMap.putAll(dicomConfiguration.getCStoreRequestInformation(as));
        return sourceMap;
    }

    @Override
    public boolean isStoreFile() {
        return true;
    }
}
