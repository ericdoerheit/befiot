package de.ericdoerheit.befiot.core;

import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Pairing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ericdorheit on 02/02/16.
 */
public class KeyAgentBuilder {
    private Logger log = LoggerFactory.getLogger(KeyAgentBuilder.class);

    private Pairing pairing;

    private int maximumNumberOfDecryptionKeyAgents;
    private Element aElem;
    private Element gElem;
    private Element vElem;
    private Element mskElem;

    private EncryptionKeyAgent encryptionKeyAgent;

    /**
     * Initialize Key Agent Builder with random parameters. Is used to create a new broadcast encryption system.
     * @param pairing
     * @param maximumNumberOfDecryptionKeyAgents
     */
    public KeyAgentBuilder(Pairing pairing, int maximumNumberOfDecryptionKeyAgents) {
        this.pairing = pairing;
        this.maximumNumberOfDecryptionKeyAgents = maximumNumberOfDecryptionKeyAgents;

        aElem = pairing.getZr().newRandomElement();
        gElem = pairing.getG1().newRandomElement();
        mskElem = pairing.getZr().newRandomElement();
        vElem = gElem.duplicate().pow(mskElem.toBigInteger());

        computeEncryptionKeyAgent();
    }

    /**
     * Initialize Key Agent Builder based on given parameters.
     * @param pairing
     * @param maximumNumberOfDecryptionKeyAgents
     * @param aElem
     * @param gElem
     * @param mskElem
     */
    public KeyAgentBuilder(Pairing pairing, int maximumNumberOfDecryptionKeyAgents, Element aElem, Element gElem, Element mskElem) {
        this.pairing = pairing;
        this.maximumNumberOfDecryptionKeyAgents = maximumNumberOfDecryptionKeyAgents;

        this.aElem = aElem;
        this.gElem = gElem;
        this.mskElem = mskElem;

        this.vElem = gElem.duplicate().pow(mskElem.toBigInteger());

        computeEncryptionKeyAgent();
    }

    /**
     * Create and return a Decryption Key Agent based on the given id.
     * @param id
     * @return
     */
    public DecryptionKeyAgent getDecryptionKeyAgent(int id) {
        List<Element> publicKey = encryptionKeyAgent.getPublicKey();
        Element gIElem = publicKey.get(id);
        Element dIElem = gIElem.duplicate().pow(mskElem.toBigInteger());

        DecryptionKeyAgent decryptionKeyAgent = new DecryptionKeyAgent(pairing, id, dIElem, publicKey);

        return decryptionKeyAgent;
    }

    /**
     * Returns the Encryption Key Agent of this Key Agent Builder containing the public key of the underlying broadcast
     * encryption system.
     * @return
     */
    public EncryptionKeyAgent getEncryptionKeyAgent() {
        return encryptionKeyAgent;
    }

    /**
     * Computes the Encryption Key Agent of this Key Agent Builder
     */
    private void computeEncryptionKeyAgent() {
        int n = this.maximumNumberOfDecryptionKeyAgents;

        ArrayList<Element> publicKey = new ArrayList<Element>(2*n+2);

        // Add g_0 = g^(a^0) = g^1 = g
        publicKey.add(gElem.duplicate());

        Element gIElem = gElem.duplicate();
        for(int i = 1; i <= 2*n; i++) {
            gIElem.pow(aElem.toBigInteger());

            if(i != n+1) {
                // Add g_i = g^(a^i)
                publicKey.add(gIElem.duplicate());
            } else {
                // Add zeroElement for g_{n+1} so that the indexes of the array are the same as the ids
                publicKey.add(pairing.getG1().newZeroElement());
            }
        }

        publicKey.add(vElem);

        this.encryptionKeyAgent = new EncryptionKeyAgent(pairing, publicKey);
    }

    public int getMaximumNumberOfDecryptionKeyAgents() {
        return maximumNumberOfDecryptionKeyAgents;
    }

    public Element getaElem() {
        return aElem;
    }

    public Element getgElem() {
        return gElem;
    }

    public Element getMskElem() {
        return mskElem;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        KeyAgentBuilder that = (KeyAgentBuilder) o;

        if (!aElem.isEqual(that.aElem)) return false;
        if (!gElem.isEqual(that.gElem)) return false;
        return mskElem.isEqual(that.mskElem);
    }
}
