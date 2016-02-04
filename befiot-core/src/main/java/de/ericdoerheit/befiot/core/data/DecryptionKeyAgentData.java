package de.ericdoerheit.befiot.core.data;

import java.util.List;

/**
 * Created by ericdorheit on 03/02/16.
 */
public class DecryptionKeyAgentData {

    private int pairingIdentifier;
    private int id;
    private byte[] privateKey;
    private List<byte[]> publicKey;

    public DecryptionKeyAgentData() {
    }

    public int getPairingIdentifier() {
        return pairingIdentifier;
    }

    public void setPairingIdentifier(int pairingIdentifier) {
        this.pairingIdentifier = pairingIdentifier;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public byte[] getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(byte[] privateKey) {
        this.privateKey = privateKey;
    }

    public List<byte[]> getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(List<byte[]> publicKey) {
        this.publicKey = publicKey;
    }
}
