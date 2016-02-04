package de.ericdoerheit.befiot.core.test;

import de.ericdoerheit.befiot.core.*;
import de.ericdoerheit.befiot.core.data.DecryptionKeyAgentData;
import de.ericdoerheit.befiot.core.data.EncryptionKeyAgentData;
import de.ericdoerheit.befiot.core.data.KeyAgentBuilderData;
import it.unisa.dia.gas.jpbc.Pairing;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

/**
 * Created by ericdorheit on 04/02/16.
 */
public class SerializationDeserializationTest {
    private Logger log = LoggerFactory.getLogger(SerializationDeserializationTest.class);

    @Test
    public void testDataSerializationDeserialization() {
        Pairing pairing = Util.getDefaultPairing();
        KeyAgentBuilder keyAgentBuilder = new KeyAgentBuilder(pairing, 5);
        EncryptionKeyAgent encryptionKeyAgent = keyAgentBuilder.getEncryptionKeyAgent();
        DecryptionKeyAgent decryptionKeyAgent = keyAgentBuilder.getDecryptionKeyAgent(2);

        KeyAgentBuilderData keyAgentBuilderData = Serializer.keyAgentBuilderToData(keyAgentBuilder);
        EncryptionKeyAgentData encryptionKeyAgentData = Serializer.encryptionKeyAgentToData(encryptionKeyAgent);
        DecryptionKeyAgentData decryptionKeyAgentData = Serializer.decryptionKeyAgentToData(decryptionKeyAgent);

        KeyAgentBuilder deserializedKeyAgentBuilder = Deserializer.keyAgentBuilderFromKeyAgentBuilderData(keyAgentBuilderData);
        EncryptionKeyAgent deserializedEncryptionKeyAgent = Deserializer.encryptionKeyAgentFromEncryptionKeyAgentData(encryptionKeyAgentData);
        DecryptionKeyAgent deserializedDecryptionKeyAgent = Deserializer.decryptionKeyAgentFromDecryptionKeyAgentData(decryptionKeyAgentData);

        assertEquals(keyAgentBuilder, deserializedKeyAgentBuilder);
        assertEquals(encryptionKeyAgent, deserializedEncryptionKeyAgent);
        assertEquals(decryptionKeyAgent, deserializedDecryptionKeyAgent);
    }

    @Test
    public void testJsonSerializationDeserialization() {
        Pairing pairing = Util.getDefaultPairing();
        KeyAgentBuilder keyAgentBuilder = new KeyAgentBuilder(pairing, 5);
        EncryptionKeyAgent encryptionKeyAgent = keyAgentBuilder.getEncryptionKeyAgent();
        DecryptionKeyAgent decryptionKeyAgent = keyAgentBuilder.getDecryptionKeyAgent(2);

        String keyAgentBuilderJson = Serializer.keyAgentBuilderToJsonString(keyAgentBuilder);
        String encryptionKeyAgentJson = Serializer.encryptionKeyAgentToJsonString(encryptionKeyAgent);
        String decryptionKeyAgentJson = Serializer.decryptionKeyAgentToJsonString(decryptionKeyAgent);

        log.debug(keyAgentBuilderJson);
        log.debug(encryptionKeyAgentJson);
        log.debug(decryptionKeyAgentJson);

        KeyAgentBuilder deserializedKeyAgentBuilder = Deserializer.jsonStringToKeyAgentBuilder(keyAgentBuilderJson);
        EncryptionKeyAgent deserializedEncryptionKeyAgent = Deserializer.jsonStringToEncryptionKeyAgent(encryptionKeyAgentJson);
        DecryptionKeyAgent deserializedDecryptionKeyAgent = Deserializer.jsonStringToDecryptionKeyAgent(decryptionKeyAgentJson);

        assertEquals(keyAgentBuilder, deserializedKeyAgentBuilder);
        assertEquals(encryptionKeyAgent, deserializedEncryptionKeyAgent);
        assertEquals(decryptionKeyAgent, deserializedDecryptionKeyAgent);
    }
}
