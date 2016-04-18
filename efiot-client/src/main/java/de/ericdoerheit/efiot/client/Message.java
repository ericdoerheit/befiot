package de.ericdoerheit.efiot.client;

import java.util.Arrays;
import java.util.Map;

/**
 * Created by ericdoerheit on 04/03/16.
 */
public class Message {
    /* --- Mandatory Attributes --- */
    private int sessionId;
    private String thingId;
    private long timestamp;
    private long notValidAfter;
    private byte[] iv;
    private byte[] encryptedMessage;
    private byte[] messageAuthenticationCode;

    /* --- Optional attributes --- */
    private byte[] messageSignature;
    private byte[] thingCertificate;
    private Map<String, byte[]> encryptedSessionKeys;
    private long sessionKeyNotValidAfter;

    public Message() {
    }

    public boolean validate(long timestamp) {
        return timestamp <= notValidAfter;
    }

    public boolean hasOptionals() {
        return messageSignature != null && thingCertificate != null && encryptedSessionKeys != null;
    }

    public String macString() {
        String macString = "";
        macString += this.getSessionId();
        macString += this.getThingId();
        macString += this.getTimestamp();
        macString += this.getNotValidAfter();
        macString += Arrays.toString(this.getIv());
        macString += Arrays.toString(this.getEncryptedMessage());

        return macString;
    }

    public String signatureString() {
        String signatureString = "";
        signatureString += this.getSessionId();
        signatureString += this.getThingId();
        signatureString += this.getTimestamp();
        signatureString += this.getNotValidAfter();
        signatureString += Arrays.toString(this.getIv());
        signatureString += Arrays.toString(this.getEncryptedMessage());
        signatureString += Arrays.toString(this.getMessageAuthenticationCode());

        signatureString += Arrays.toString(this.getThingCertificate());

        for (Map.Entry<String, byte[]> entry : this.getEncryptedSessionKeys().entrySet()) {
            signatureString += entry.getKey() + Arrays.toString(entry.getValue());
        }

        signatureString += this.getSessionKeyNotValidAfter();

        return signatureString;
    }

    @Override
    public String toString() {
        return "Message{" +
                "sessionId=" + sessionId +
                ", thingId='" + thingId + '\'' +
                ", timestamp=" + timestamp +
                ", notValidAfter=" + notValidAfter +
                ", iv=" + Arrays.hashCode(iv) +
                ", encryptedMessage=" + Arrays.hashCode(encryptedMessage) +
                ", messageAuthenticationCode=" + Arrays.hashCode(messageAuthenticationCode) +
                ", messageSignature=" + Arrays.hashCode(messageSignature) +
                ", thingCertificate=" + Arrays.hashCode(thingCertificate) +
                ", broadcastEncryptedSessionKeys=" + ClientUtil.byteMapToString(encryptedSessionKeys) +
                ", sessionKeyNotValidAfter=" + sessionKeyNotValidAfter +
                '}';
    }

    public int getSessionId() {
        return sessionId;
    }

    public void setSessionId(int sessionId) {
        this.sessionId = sessionId;
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

    public Map<String, byte[]> getEncryptedSessionKeys() {
        return encryptedSessionKeys;
    }

    public void setEncryptedSessionKeys(Map<String, byte[]> encryptedSessionKeys) {
        this.encryptedSessionKeys = encryptedSessionKeys;
    }

    public long getSessionKeyNotValidAfter() {
        return sessionKeyNotValidAfter;
    }

    public void setSessionKeyNotValidAfter(long sessionKeyNotValidAfter) {
        this.sessionKeyNotValidAfter = sessionKeyNotValidAfter;
    }
}
