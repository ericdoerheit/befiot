package de.ericdoerheit.befiot.core;

import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Pairing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by ericdorheit on 02/02/16.
 */
public class EncryptionKeyAgent {
    private static final Logger log = LoggerFactory.getLogger(EncryptionKeyAgent.class);

    private long validNotBefore;
    private long validNotAfter;

    private Pairing pairing;
    private List<Element> publicKey;

    private int n;
    private Element eElem;

    private Element keyElem;
    private Element c0Elem;
    private Element c1Elem;

    /**
     * Initialize the Decryption Key Agent with given public key.
     * @param publicKey
     * @param pairing
     * @param validNotBefore
     * @param validNotAfter
     */
    public EncryptionKeyAgent(long validNotBefore, long validNotAfter, Pairing pairing, List<Element> publicKey) {
        this.validNotBefore = validNotBefore;
        this.validNotAfter = validNotAfter;
        this.pairing = pairing;
        this.publicKey = publicKey;

        // publicKey.size() == 2n + 2 <=> n = (publicKey.size() - 2) / 2
        this.n = (publicKey.size() - 2) / 2;
        Element gNElem = publicKey.get(n);
        Element g1Elem = publicKey.get(1);
        this.eElem = pairing.pairing(gNElem, g1Elem);
    }

    /**
     * This method returns the current meta data object.
     * @return
     */
    public EncryptionHeader getHeader() {
        EncryptionHeader encryptionHeader = new EncryptionHeader();
        encryptionHeader.setC0Elem(this.c0Elem);
        encryptionHeader.setC1Elem(this.c1Elem);

        return encryptionHeader;
    }

    /**
     * This method returns the current session key.
     * @return
     */
    public Element getKey() {
        return keyElem;
    }

    /**
     * This method returns the current session as byte array.
     * @return
     */
    public byte[] getKeyBytes() {
        return keyElem.toBytes();
    }

    /**
     * This method generates a new random and creates a new session key as well as the associated header data based on
     * the public key and the set of authorized recipients given by an array of their ids.
     */
    public void next(int[] ids) {
        // Pick random t
        Element t = pairing.getZr().newRandomElement();

        /* --- Compute Key --- */
        this.keyElem = eElem.duplicate().pow(t.toBigInteger());

        /* --- Compute Header ---*/
        Element gElem = publicKey.get(0);
        this.c0Elem = gElem.duplicate().pow(t.toBigInteger());

        // vElem is the last element of the public key
        Element vElem = publicKey.get(publicKey.size()-1);

        Element productElem = vElem.duplicate();

        for (int i = 0; i < ids.length; i++) {
            log.debug("Multiply g_{} to product.", n+1-ids[i]);

            int k = n+1-ids[i];
            log.debug("n={}, id={}, k={}", n, ids[i], k);
            productElem.mul(publicKey.get(k));
        }

        this.c1Elem = productElem.pow(t.toBigInteger());

        log.debug("Encryption for {} users. A total of n = {} users are in the system. PK size = {}.", ids.length, n, publicKey.size());
        log.trace("C0: {}, C1: {}, IDs: {}", Arrays.hashCode(this.c0Elem.toBytes()), Arrays.hashCode(this.c1Elem.toBytes()), Arrays.toString(ids));
    }

    public boolean validate(long timestamp) {
        return validNotBefore <= timestamp && timestamp <= validNotAfter;
    }

    public List<Element> getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(List<Element> publicKey) {
        this.publicKey = publicKey;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EncryptionKeyAgent that = (EncryptionKeyAgent) o;

        // Compare second and last element of public key (g^a and v)
        if (!publicKey.get(publicKey.size()-1).isEqual(that.getPublicKey().get(publicKey.size()-1))) return false;
        return publicKey.get(1).isEqual(that.getPublicKey().get(1));
    }
}
