package de.ericdoerheit.befiot.core;

import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Pairing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by ericdorheit on 02/02/16.
 */
public class DecryptionKeyAgent {
    private static final Logger log = LoggerFactory.getLogger(DecryptionKeyAgent.class);

    private long validNotBefore;
    private long validNotAfter;

    private Pairing pairing;
    private int id;
    private Element privateKey;
    private List<Element> publicKey;
    private Element currentKey;

    /**
     * Initialize the Decryption Key Agent with given parameters.
     * @param id
     * @param privateKey
     * @param publicKey
     * @param validNotBefore
     * @param validNotAfter
     */
    public DecryptionKeyAgent(long validNotBefore, long validNotAfter, Pairing pairing, int id, Element privateKey, List<Element> publicKey) {
        this.validNotBefore = validNotBefore;
        this.validNotAfter = validNotAfter;
        this.pairing = pairing;
        this.id = id;
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    /**
     * This method decrypts the session key based on the information given in the header and the private key stored
     * in the Decryption Key Agent. The header contains C0, C1 and the description of the set of authorized users.
     * @return
     */
    public Element getKey(EncryptionHeader header, int[] ids) {
        if (header != null && header.getC0Elem() != null && header.getC1Elem() != null && ids != null) {
            log.debug("C0: {}, C1: {}, IDs: {}", Arrays.hashCode(header.getC0Elem().toBytes()), Arrays.hashCode(header.getC1Elem().toBytes()), Arrays.toString(ids));
            Element c0Elem = header.getC0Elem();
            Element c1Elem = header.getC1Elem();
            Element gIElem = publicKey.get(id);

            Element e1Elem = pairing.pairing(gIElem, c1Elem);

            Element productElem = privateKey.duplicate();
            log.debug("Product base is d_{}.", id);

            // publicKey.size() == 2n + 2 <=> n = (publicKey.size() - 2) / 2
            int n = (publicKey.size() - 2) / 2;
            for (int i = 0; i < ids.length; i++) {

                int j = ids[i];

                if (j != id) {
                    int k = n + 1 - j + id;
                    Element gKElem = publicKey.get(k);
                    log.debug("Multiply g_{} to product.", k);
                    productElem.mul(gKElem);
                }
            }

            Element e2Elem = pairing.pairing(productElem, c0Elem);

            log.debug("Decrypt key which is available for {} users. This Decryption Key Agent has id {}.", n, id);
            currentKey = e1Elem.div(e2Elem);
            return currentKey;
        }

        log.warn("IDs, header or header element is null. Header: {}, IDs: {}", header, Arrays.toString(ids));
        return null;
    }

    public boolean validate(long timestamp) {
        return validNotBefore <= timestamp && timestamp <= validNotAfter;
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

    public byte[] getKeyBytes(EncryptionHeader header, int[] ids) {
        return getKey(header, ids).toBytes();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Element getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(Element privateKey) {
        this.privateKey = privateKey;
    }

    public List<Element> getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(List<Element> publicKey) {
        this.publicKey = publicKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DecryptionKeyAgent that = (DecryptionKeyAgent) o;

        // Compare private key and second and last element of public key (g^a and v)
        if (!privateKey.isEqual(that.getPrivateKey())) return false;
        if (!publicKey.get(publicKey.size()-1).isEqual(that.getPublicKey().get(publicKey.size()-1))) return false;
        return publicKey.get(1).isEqual(that.getPublicKey().get(1));
    }

    @Override
    public int hashCode() {
        int result = (int) (validNotBefore ^ (validNotBefore >>> 32));
        result = 31 * result + (int) (validNotAfter ^ (validNotAfter >>> 32));
        result = 31 * result + id;
        result = 31 * result + (privateKey != null ? privateKey.hashCode() : 0);
        result = 31 * result + (publicKey != null ? publicKey.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DecryptionKeyAgent{" +
                "validNotBefore=" + validNotBefore +
                ", validNotAfter=" + validNotAfter +
                ", id=" + id +
                ", privateKey=" + (privateKey != null ? Arrays.toString(privateKey.toBytes()).hashCode() : privateKey) +
                ", publicKey=" + (publicKey != null ? publicKey.hashCode() : publicKey) +
                ", currentKey=" + currentKey +
                '}';
    }
}
