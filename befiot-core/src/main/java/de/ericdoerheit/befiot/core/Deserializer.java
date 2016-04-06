package de.ericdoerheit.befiot.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ericdoerheit.befiot.core.data.*;
import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Pairing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ericdorheit on 04/02/16.
 */
public class Deserializer {
    private static final Logger log = LoggerFactory.getLogger(Deserializer.class);

    // TODO: 04/02/16 Use / Implement Pairing Identifier

    private static ObjectMapper mapper = new ObjectMapper();

    /* --- Decryption Key Agent --- */
    public static DecryptionKeyAgent decryptionKeyAgentFromDecryptionKeyAgentData(DecryptionKeyAgentData
                                                                                          decryptionKeyAgentData) {

        long validNotBefore = decryptionKeyAgentData.getValidNotBefore();
        long validNotAfter = decryptionKeyAgentData.getValidNotAfter();

        Pairing pairing = Util.getDefaultPairing();
        Element privateKey = pairing.getG1().newElementFromBytes(decryptionKeyAgentData.getPrivateKey());
        List<Element> publicKey = new ArrayList<Element>();
        for (byte[] b : decryptionKeyAgentData.getPublicKey()) {
            publicKey.add(pairing.getG1().newElementFromBytes(b));
        }
        
        DecryptionKeyAgent decryptionKeyAgent = new DecryptionKeyAgent(validNotBefore, validNotAfter, pairing,
                decryptionKeyAgentData.getId(), privateKey, publicKey);

        return decryptionKeyAgent;
    }

    public static DecryptionKeyAgentData jsonStringToDecryptionKeyAgentData(String jsonString) {
        try {
            DecryptionKeyAgentData decryptionKeyAgentData = mapper.readValue(jsonString, DecryptionKeyAgentData.class);
            return decryptionKeyAgentData;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
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
    public static EncryptionKeyAgent encryptionKeyAgentFromEncryptionKeyAgentData(EncryptionKeyAgentData
                                                                                          encryptionKeyAgentData) {
        long validNotBefore = encryptionKeyAgentData.getValidNotBefore();
        long validNotAfter = encryptionKeyAgentData.getValidNotAfter();


        Pairing pairing = Util.getDefaultPairing();
        List<Element> publicKey = new ArrayList<Element>();
        for (byte[] b : encryptionKeyAgentData.getPublicKey()) {
            publicKey.add(pairing.getG1().newElementFromBytes(b));
        }

        EncryptionKeyAgent encryptionKeyAgent = new EncryptionKeyAgent(validNotBefore, validNotAfter,
                pairing, publicKey);

        return encryptionKeyAgent;
    }

    public static EncryptionKeyAgentData jsonStringToEncryptionKeyAgentData(String jsonString) {
        try {
            EncryptionKeyAgentData encryptionKeyAgentData = mapper.readValue(jsonString, EncryptionKeyAgentData.class);
            return encryptionKeyAgentData;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
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

        long validNotBefore = keyAgentBuilderData.getValidNotBefore();
        long validNotAfter = keyAgentBuilderData.getValidNotAfter();

        Pairing pairing = Util.getDefaultPairing();
        Element aElem = pairing.getZr().newElementFromBytes(keyAgentBuilderData.getA());
        Element gElem = pairing.getG1().newElementFromBytes(keyAgentBuilderData.getG());
        Element mskElem = pairing.getZr().newElementFromBytes(keyAgentBuilderData.getMsk());

        KeyAgentBuilder keyAgentBuilder = new KeyAgentBuilder(validNotBefore, validNotAfter, pairing,
                keyAgentBuilderData.getMaximumNumberOfDecryptionKeyAgents(), aElem, gElem, mskElem);

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

    /* --- Tenant Data --- */
    public static TenantData jsonStringToTenantData(String jsonString) {
        try {
            return mapper.readValue(jsonString, TenantData.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /* --- Thing Data --- */
    public static ThingData jsonStringToThingData(String jsonString) {
        try {
            return mapper.readValue(jsonString, ThingData.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /* --- Registry Data --- */
    public static RegistryData jsonStringToRegistryData(String jsonString) {
        try {
            return mapper.readValue(jsonString, RegistryData.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /* --- Encryption Header --- */
    public static EncryptionHeader encryptionHeaderFromEncryptionHeaderData(EncryptionHeaderData encryptionHeaderData) {
        Pairing pairing = Util.getDefaultPairing();
        Element c0Elem = pairing.getG1().newElementFromBytes(encryptionHeaderData.getC0());
        Element c1Elem = pairing.getG1().newElementFromBytes(encryptionHeaderData.getC1());

        EncryptionHeader encryptionHeader = new EncryptionHeader();
        encryptionHeader.setC0Elem(c0Elem);
        encryptionHeader.setC1Elem(c1Elem);

        return encryptionHeader;
    }

    public static EncryptionHeader jsonStringToEncryptionHeader(String jsonString) {
        try {
            EncryptionHeaderData encryptionHeaderData = mapper.readValue(jsonString, EncryptionHeaderData.class);
            return encryptionHeaderFromEncryptionHeaderData(encryptionHeaderData);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
