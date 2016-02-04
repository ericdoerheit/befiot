package de.ericdoerheit.befiot.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ericdoerheit.befiot.core.data.DecryptionKeyAgentData;
import de.ericdoerheit.befiot.core.data.EncryptionKeyAgentData;
import de.ericdoerheit.befiot.core.data.KeyAgentBuilderData;
import it.unisa.dia.gas.jpbc.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by ericdorheit on 02/02/16.
 */
public class Serializer {
    private Logger log = LoggerFactory.getLogger(Serializer.class);

    // TODO: 04/02/16 Pairing Identifier

    private static ObjectMapper mapper = new ObjectMapper();
    
    /* --- Decryption Key Agent --- */
    public static DecryptionKeyAgentData decryptionKeyAgentToData(DecryptionKeyAgent decryptionKeyAgent) {
        DecryptionKeyAgentData decryptionKeyAgentData = new DecryptionKeyAgentData();
        decryptionKeyAgentData.setId(decryptionKeyAgent.getId());
        decryptionKeyAgentData.setPrivateKey(decryptionKeyAgent.getPrivateKey().toBytes());

        List<byte[]> publicKey = new ArrayList<byte[]>();
        for (Element e : decryptionKeyAgent.getPublicKey()) {
            publicKey.add(e.toBytes());
        }
        decryptionKeyAgentData.setPublicKey(publicKey);

        return decryptionKeyAgentData;
    }
    public static String decryptionKeyAgentToJsonString(DecryptionKeyAgent decryptionKeyAgent) {
        try {
            return mapper.writeValueAsString(decryptionKeyAgentToData(decryptionKeyAgent));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    /* --- Encryption Key Agent --- */
    public static EncryptionKeyAgentData encryptionKeyAgentToData(EncryptionKeyAgent encryptionKeyAgent) {
        EncryptionKeyAgentData encryptionKeyAgentData = new EncryptionKeyAgentData();

        List<byte[]> publicKey = new ArrayList<byte[]>();
        for (Element e : encryptionKeyAgent.getPublicKey()) {
            publicKey.add(e.toBytes());
        }
        encryptionKeyAgentData.setPublicKey(publicKey);

        return encryptionKeyAgentData;
    }
    public static String encryptionKeyAgentToJsonString(EncryptionKeyAgent encryptionKeyAgent) {
        try {
            return mapper.writeValueAsString(encryptionKeyAgentToData(encryptionKeyAgent));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    /* --- Key Agent Builder --- */
    public static KeyAgentBuilderData keyAgentBuilderToData(KeyAgentBuilder keyAgentBuilder) {
        KeyAgentBuilderData keyAgentBuilderData = new KeyAgentBuilderData();
        keyAgentBuilderData.setA(keyAgentBuilder.getaElem().toBytes());
        keyAgentBuilderData.setG(keyAgentBuilder.getgElem().toBytes());
        keyAgentBuilderData.setMaximumNumberOfDecryptionKeyAgents(keyAgentBuilder.getMaximumNumberOfDecryptionKeyAgents());
        keyAgentBuilderData.setMsk(keyAgentBuilder.getMskElem().toBytes());

        return keyAgentBuilderData;
    }
    public static String keyAgentBuilderToJsonString(KeyAgentBuilder keyAgentBuilder) {
        try {
            return mapper.writeValueAsString(keyAgentBuilderToData(keyAgentBuilder));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }
}
