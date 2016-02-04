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
public class DecryptionKeyAgent {
    private Logger log = LoggerFactory.getLogger(DecryptionKeyAgent.class);

    private Pairing pairing;
    private int id;
    private Element privateKey;
    private List<Element> publicKey;

    /**
     * Initialize the Decryption Key Agent with given parameters.
     * @param id
     * @param privateKey
     * @param publicKey
     */
    public DecryptionKeyAgent(Pairing pairing, int id, Element privateKey, List<Element> publicKey) {
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
    public Element getKey(List<Element> header, int[] ids) {
        Element c0Elem = header.get(0);
        Element c1Elem = header.get(1);
        Element gIElem = publicKey.get(id);

        Element e1Elem = pairing.pairing(gIElem, c1Elem);

        Element productElem = privateKey.duplicate();
        log.debug("Product base is d_{}.", id);

        // publicKey.size() == 2n + 2 <=> n = (publicKey.size() - 2) / 2
        int n = (publicKey.size() - 2) / 2;
        for(int i = 0; i < ids.length; i++) {

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
        return e1Elem.div(e2Elem);
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
}
