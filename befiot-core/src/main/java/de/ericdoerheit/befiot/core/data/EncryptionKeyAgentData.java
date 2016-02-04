package de.ericdoerheit.befiot.core.data;

import java.util.List;

/**
 * Created by ericdorheit on 03/02/16.
 */
public class EncryptionKeyAgentData {

    private int pairingIdentifier;
    private List<byte[]> publicKey;

    public EncryptionKeyAgentData() {
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
