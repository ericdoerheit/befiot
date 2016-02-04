package de.ericdoerheit.befiot.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ericdoerheit.befiot.core.data.DecryptionKeyAgentData;
import de.ericdoerheit.befiot.core.data.EncryptionKeyAgentData;
import de.ericdoerheit.befiot.core.data.KeyAgentBuilderData;
import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Pairing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ericdorheit on 04/02/16.
 */
public class Deserializer {

    // TODO: 04/02/16 Use / Implement Pairing Identifier

    private static ObjectMapper mapper = new ObjectMapper();

    /* --- Decryption Key Agent --- */
    public static DecryptionKeyAgent decryptionKeyAgentFromDecryptionKeyAgentData(DecryptionKeyAgentData decryptionKeyAgentData) {

        Pairing pairing = Util.getDefaultPairing();
        Element privateKey = pairing.getG1().newElementFromBytes(decryptionKeyAgentData.getPrivateKey());
        List<Element> publicKey = new ArrayList<Element>();
        for (byte[] b : decryptionKeyAgentData.getPublicKey()) {
            publicKey.add(pairing.getG1().newElementFromBytes(b));
        }
        
        DecryptionKeyAgent decryptionKeyAgent = new DecryptionKeyAgent(pairing, decryptionKeyAgentData.getId(),
                privateKey, publicKey);

        return decryptionKeyAgent;
    }
    public static DecryptionKeyAgent jsonStringToDecryptionKeyAgent(String jsonString) {
        try {
            DecryptionKeyAgentData decryptionKeyAgentData = mapper.readValue(jsonString, DecryptionKeyAgentData.class);
            return decryptionKeyAgentFromDecryptionKeyAgentData(decryptionKeyAgentData);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /* --- Encryption Key Agent --- */
    public static EncryptionKeyAgent encryptionKeyAgentFromEncryptionKeyAgentData(EncryptionKeyAgentData encryptionKeyAgentData) {
        Pairing pairing = Util.getDefaultPairing();
        List<Element> publicKey = new ArrayList<Element>();
        for (byte[] b : encryptionKeyAgentData.getPublicKey()) {
            publicKey.add(pairing.getG1().newElementFromBytes(b));
        }

        EncryptionKeyAgent encryptionKeyAgent = new EncryptionKeyAgent(pairing, publicKey);

        return encryptionKeyAgent;
    }
    public static EncryptionKeyAgent jsonStringToEncryptionKeyAgent(String jsonString) {
        try {
            EncryptionKeyAgentData encryptionKeyAgentData = mapper.readValue(jsonString, EncryptionKeyAgentData.class);
            return encryptionKeyAgentFromEncryptionKeyAgentData(encryptionKeyAgentData);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /* --- Key Agent Builder --- */
    public static KeyAgentBuilder keyAgentBuilderFromKeyAgentBuilderData(KeyAgentBuilderData keyAgentBuilderData) {

        Pairing pairing = Util.getDefaultPairing();
        Element aElem = pairing.getZr().newElementFromBytes(keyAgentBuilderData.getA());
        Element gElem = pairing.getG1().newElementFromBytes(keyAgentBuilderData.getG());
        Element mskElem = pairing.getZr().newElementFromBytes(keyAgentBuilderData.getMsk());

        KeyAgentBuilder keyAgentBuilder = new KeyAgentBuilder(pairing, keyAgentBuilderData.getMaximumNumberOfDecryptionKeyAgents(),
                aElem, gElem, mskElem);

        return keyAgentBuilder;
    }
    public static KeyAgentBuilder jsonStringToKeyAgentBuilder(String jsonString) {
        try {
            KeyAgentBuilderData keyAgentBuilderData = mapper.readValue(jsonString, KeyAgentBuilderData.class);
            return keyAgentBuilderFromKeyAgentBuilderData(keyAgentBuilderData);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
