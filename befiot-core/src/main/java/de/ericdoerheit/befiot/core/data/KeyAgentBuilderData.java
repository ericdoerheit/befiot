package de.ericdoerheit.befiot.core.data;


/**
 * Created by ericdorheit on 03/02/16.
 */
public class KeyAgentBuilderData {

    private long validNotBefore;
    private long validNotAfter;

    private int pairingIdentifier;
    private int maximumNumberOfDecryptionKeyAgents;
    private byte[] a;
    private byte[] g;
    private byte[] msk;

    public KeyAgentBuilderData() {
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

    public int getPairingIdentifier() {
        return pairingIdentifier;
    }

    public void setPairingIdentifier(int pairingIdentifier) {
        this.pairingIdentifier = pairingIdentifier;
    }

    public int getMaximumNumberOfDecryptionKeyAgents() {
        return maximumNumberOfDecryptionKeyAgents;
    }

    public void setMaximumNumberOfDecryptionKeyAgents(int maximumNumberOfDecryptionKeyAgents) {
        this.maximumNumberOfDecryptionKeyAgents = maximumNumberOfDecryptionKeyAgents;
    }

    public byte[] getA() {
        return a;
    }

    public void setA(byte[] a) {
        this.a = a;
    }

    public byte[] getG() {
        return g;
    }

    public void setG(byte[] g) {
        this.g = g;
    }

    public byte[] getMsk() {
        return msk;
    }

    public void setMsk(byte[] msk) {
        this.msk = msk;
    }
}
