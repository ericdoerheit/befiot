package de.ericdoerheit.befiot.core.data;

import java.util.List;

/**
 * Created by ericdorheit on 03/02/16.
 */
public class EncryptionKeyAgentData {

    private long validNotBefore;
    private long validNotAfter;

    private String subjectName; // Tenant ID
    private String issuerName; // Name of signing CA
    private byte[] digitalSignature;

    private int pairingIdentifier;
    private List<byte[]> publicKey;

    public EncryptionKeyAgentData() {
    }

    public long getValidNotBefore() {
        return validNotBefore;
    }

    public void setValidNotBefore(long validNotBefore) {
        this.validNotBefore = validNotBefore;
    }

    public long getValidNotAfter() {
        return validNotAfter;
    }

    public void setValidNotAfter(long validNotAfter) {
        this.validNotAfter = validNotAfter;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public String getIssuerName() {
        return issuerName;
    }

    public void setIssuerName(String issuerName) {
        this.issuerName = issuerName;
    }

    public byte[] getDigitalSignature() {
        return digitalSignature;
    }

    public void setDigitalSignature(byte[] digitalSignature) {
        this.digitalSignature = digitalSignature;
    }

    public int getPairingIdentifier() {
        return pairingIdentifier;
    }

    public void setPairingIdentifier(int pairingIdentifier) {
        this.pairingIdentifier = pairingIdentifier;
    }

    public List<byte[]> getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(List<byte[]> publicKey) {
        this.publicKey = publicKey;
    }
}
