package de.ericdoerheit.befiot.client;

import de.ericdoerheit.befiot.core.data.EncryptionHeaderData;

import java.util.Arrays;
import java.util.Map;

/**
 * Created by ericdoerheit on 04/03/16.
 */
public class Message {
    /* --- Mandatory Attributes --- */
    private int sessionId;
    private String tenantId;
    private String thingId;
    private long timestamp;
    private long notValidAfter;
    private byte[] iv;
    private byte[] encryptedMessage;
    private byte[] messageAuthenticationCode;

    /* --- Optional attributes --- */
    private byte[] messageSignature;
    private byte[] thingCertificate;
    private Map<String, EncryptionHeaderData> broadcastEncryptionHeaders;
    private Map<String, byte[]> broadcastEncryptedSessionKeys;
    private Map<String, int[]> broadcastEncryptionIds;
    private long sessionKeyNotValidAfter;

    public Message() {
    }

    public boolean validate(long timestamp) {
        return timestamp <= notValidAfter;
    }

    public boolean hasOptionals() {
        return messageSignature != null && thingCertificate != null
                && broadcastEncryptionHeaders != null && broadcastEncryptedSessionKeys != null
                && broadcastEncryptionIds != null;
    }

    public String macString() {
        String macString = "";
        macString += this.getSessionId();
        macString += this.getTenantId();
        macString += this.getThingId();
        macString += this.getTimestamp();
        macString += Arrays.toString(this.getIv());
        macString += Arrays.toString(this.getEncryptedMessage());

        return macString;
    }

    public String signatureString() {
        String signatureString = "";
        signatureString += this.getSessionId();
        signatureString += this.getTenantId();
        signatureString += this.getThingId();
        signatureString += this.getTimestamp();
        signatureString += Arrays.toString(this.getIv());
        signatureString += Arrays.toString(this.getEncryptedMessage());
        signatureString += Arrays.toString(this.getMessageAuthenticationCode());

        signatureString += Arrays.toString(this.getThingCertificate());

        for (Map.Entry<String, EncryptionHeaderData> entry : this.getBroadcastEncryptionHeaders().entrySet()) {
            signatureString += entry.getKey() + Arrays.toString(entry.getValue().getC0()) + Arrays.toString(entry.getValue().getC1());
        }

        for (Map.Entry<String, byte[]> entry : this.getBroadcastEncryptedSessionKeys().entrySet()) {
            signatureString += entry.getKey() + Arrays.toString(entry.getValue());
        }

        for (Map.Entry<String, int[]> entry : this.getBroadcastEncryptionIds().entrySet()) {
            signatureString += entry.getKey() + Arrays.toString(entry.getValue());
        }

        signatureString += this.getSessionKeyNotValidAfter();

        return signatureString;
    }

    public int getSessionId() {
        return sessionId;
    }

    public void setSessionId(int sessionId) {
        this.sessionId = sessionId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getThingId() {
        return thingId;
    }

    public void setThingId(String thingId) {
        this.thingId = thingId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getNotValidAfter() {
        return notValidAfter;
    }

    public void setNotValidAfter(long notValidAfter) {
        this.notValidAfter = notValidAfter;
    }

    public byte[] getIv() {
        return iv;
    }

    public void setIv(byte[] iv) {
        this.iv = iv;
    }

    public byte[] getEncryptedMessage() {
        return encryptedMessage;
    }

    public void setEncryptedMessage(byte[] encryptedMessage) {
        this.encryptedMessage = encryptedMessage;
    }

    public byte[] getMessageAuthenticationCode() {
        return messageAuthenticationCode;
    }

    public void setMessageAuthenticationCode(byte[] messageAuthenticationCode) {
        this.messageAuthenticationCode = messageAuthenticationCode;
    }

    public byte[] getMessageSignature() {
        return messageSignature;
    }

    public void setMessageSignature(byte[] messageSignature) {
        this.messageSignature = messageSignature;
    }

    public byte[] getThingCertificate() {
        return thingCertificate;
    }

    public void setThingCertificate(byte[] thingCertificate) {
        this.thingCertificate = thingCertificate;
    }

    public Map<String, EncryptionHeaderData> getBroadcastEncryptionHeaders() {
        return broadcastEncryptionHeaders;
    }

    public void setBroadcastEncryptionHeaders(Map<String, EncryptionHeaderData> broadcastEncryptionHeaders) {
        this.broadcastEncryptionHeaders = broadcastEncryptionHeaders;
    }

    public Map<String, byte[]> getBroadcastEncryptedSessionKeys() {
        return broadcastEncryptedSessionKeys;
    }

    public void setBroadcastEncryptedSessionKeys(Map<String, byte[]> broadcastEncryptedSessionKeys) {
        this.broadcastEncryptedSessionKeys = broadcastEncryptedSessionKeys;
    }

    public Map<String, int[]> getBroadcastEncryptionIds() {
        return broadcastEncryptionIds;
    }

    public void setBroadcastEncryptionIds(Map<String, int[]> broadcastEncryptionIds) {
        this.broadcastEncryptionIds = broadcastEncryptionIds;
    }

    public long getSessionKeyNotValidAfter() {
        return sessionKeyNotValidAfter;
    }

    public void setSessionKeyNotValidAfter(long sessionKeyNotValidAfter) {
        this.sessionKeyNotValidAfter = sessionKeyNotValidAfter;
    }

    @Override
    public String toString() {
        return "Message{" +
                "sessionId=" + sessionId +
                ", tenantId='" + tenantId + '\'' +
                ", thingId='" + thingId + '\'' +
                ", timestamp=" + timestamp +
                ", notValidAfter=" + notValidAfter +
                ", iv=" + Arrays.hashCode(iv) +
                ", encryptedMessage=" + Arrays.hashCode(encryptedMessage) +
                ", messageAuthenticationCode=" + Arrays.hashCode(messageAuthenticationCode) +
                ", messageSignature=" + Arrays.hashCode(messageSignature) +
                ", thingCertificate=" + Arrays.hashCode(thingCertificate) +
                ", broadcastEncryptionHeaders=" + broadcastEncryptionHeaders +
                ", broadcastEncryptedSessionKeys=" + ClientUtil.byteMapToString(broadcastEncryptedSessionKeys) +
                ", broadcastEncryptionIds=" + ClientUtil.intMapToString(broadcastEncryptionIds) +
                ", sessionKeyNotValidAfter=" + sessionKeyNotValidAfter +
                '}';
    }
}
