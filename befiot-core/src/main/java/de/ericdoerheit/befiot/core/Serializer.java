package de.ericdoerheit.befiot.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ericdoerheit.befiot.core.data.*;
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
    private static final Logger log = LoggerFactory.getLogger(Serializer.class);

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

    /* --- Tenant Data --- */
    public static String tenantDataToJsonString(TenantData tenantData) {
        try {
            return mapper.writeValueAsString(tenantData);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    /* --- Thing Data --- */
    public static String thingDataToJsonString(ThingData thingData) {
        try {
            return mapper.writeValueAsString(thingData);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    /* --- Registy Data --- */
    public static String registryDataToJsonString(RegistryData registryData) {
        try {
            return mapper.writeValueAsString(registryData);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    /* --- Encryption Header --- */
    public static String encryptionHeaderToJsonString(EncryptionHeader encryptionHeader) {
        EncryptionHeaderData encryptionHeaderData = new EncryptionHeaderData();
        encryptionHeaderData.setC0(encryptionHeader.getC0Elem().toBytes());
        encryptionHeaderData.setC1(encryptionHeader.getC1Elem().toBytes());

        try {
            return mapper.writeValueAsString(encryptionHeaderData);
        } catch (JsonProcessingException e) {
            log.error(e.getMessage());
        }

        return null;
    }
}
