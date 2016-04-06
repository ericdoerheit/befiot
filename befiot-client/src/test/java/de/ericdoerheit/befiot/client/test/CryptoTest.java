package de.ericdoerheit.befiot.client.test;

import de.ericdoerheit.befiot.client.CryptographyUtil;
import de.ericdoerheit.befiot.client.Message;
import de.ericdoerheit.befiot.core.data.EncryptionHeaderData;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.*;
import java.security.cert.*;
import java.security.spec.InvalidKeySpecException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Created by ericdoerheit on 05/03/16.
 */
public class CryptoTest {
    private Logger log = LoggerFactory.getLogger(CryptoTest.class);

    @Test
    public void secureRandomTest() {

        byte[] hash = new byte[] {0x00, 0x01, 0x00, 0x02, 0x3a, 0x49, 0x00, 0x01, 0x00, 0x02, 0x3a, 0x49, 0x00, 0x01, 0x00, 0x02};

        CryptographyUtil cryptographyUtil = new CryptographyUtil();
        try {
            assertArrayEquals(cryptographyUtil.keyFromHash(hash), cryptographyUtil.keyFromHash(hash));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void aesTest() {
        CryptographyUtil cryptographyUtil = new CryptographyUtil();

        byte[] key = new byte[0];
        try {
            key = cryptographyUtil.randomKey();

            byte[] iv = new byte[] {0x00, 0x01, 0x00, 0x02, 0x3a, 0x49, 0x00, 0x01,
                    0x00, 0x02, 0x3a, 0x49, 0x00, 0x01, 0x00, 0x02};

            byte[] message = "Bla".getBytes();

            byte[] encryptedMessage = cryptographyUtil.aesEncrypt(key, iv, message, true);
            byte[] decryptedMessage = cryptographyUtil.aesDecrypt(key, iv, encryptedMessage, true);

            assertArrayEquals(message, decryptedMessage);

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        } catch (ShortBufferException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void macTest() throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {
        CryptographyUtil cryptographyUtil = new CryptographyUtil();

        Message testMessage = new Message();
        testMessage.setSessionId(1);
        testMessage.setTenantId("tenant");
        testMessage.setThingId("thing");
        testMessage.setTimestamp(System.currentTimeMillis());
        testMessage.setNotValidAfter(System.currentTimeMillis()+100000);
        testMessage.setIv(new byte[]{0x00, 0x01, 0x02, 0x03});
        testMessage.setEncryptedMessage(new byte[]{0x00, 0x01, 0x02, 0x03});

        byte[] key = cryptographyUtil.randomKey();
        testMessage.setMessageAuthenticationCode(cryptographyUtil.messageAuthenticationCode(key, testMessage));
        assertTrue(cryptographyUtil.checkMessageAuthenticationCode(key, testMessage));
    }

    @Test
    public void ecdsaTest() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException,
            UnrecoverableKeyException, InvalidKeySpecException, NoSuchProviderException, InvalidKeyException,
            SignatureException {

        final String KEY_STORE_LOCATION = "keystore.jks";
        final String KEY_STORE_ALIAS = "localhost";
        final String KEY_STORE_PASSWORD = "befiot";

        CryptographyUtil cryptographyUtil = new CryptographyUtil();

        KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(new FileInputStream(KEY_STORE_LOCATION), KEY_STORE_PASSWORD.toCharArray());
        java.security.cert.Certificate thingCertificate = keystore.getCertificate(KEY_STORE_ALIAS);
        PrivateKey thingPrivateKey = (PrivateKey) keystore.getKey(KEY_STORE_ALIAS, KEY_STORE_PASSWORD.toCharArray());



        for (Enumeration<String> e = keystore.aliases(); e.hasMoreElements(); ) {
            System.out.println(e.nextElement());
        }

        Message testMessage = new Message();
        testMessage.setSessionId(1);
        testMessage.setTenantId("tenant");
        testMessage.setThingId("thing");
        testMessage.setTimestamp(System.currentTimeMillis());
        testMessage.setNotValidAfter(System.currentTimeMillis()+100000);
        testMessage.setIv(new byte[]{0x00, 0x01, 0x02, 0x03});
        testMessage.setEncryptedMessage(new byte[]{0x00, 0x01, 0x02, 0x03});
        testMessage.setMessageAuthenticationCode(new byte[]{0x00, 0x01, 0x02, 0x03});

        testMessage.setThingCertificate(thingCertificate.getEncoded());
        testMessage.setSessionKeyNotValidAfter(System.currentTimeMillis()+100000);

        Map<String, EncryptionHeaderData> broadcastEncryptionHeaders = new HashMap<String, EncryptionHeaderData>();
        EncryptionHeaderData header1 = new EncryptionHeaderData();
        header1.setC0(new byte[]{0x00, 0x01, 0x02, 0x03});
        header1.setC1(new byte[]{0x00, 0x01, 0x02, 0x03});
        broadcastEncryptionHeaders.put("Header1", header1);
        EncryptionHeaderData header2 = new EncryptionHeaderData();
        header2.setC0(new byte[]{0x00, 0x01, 0x02, 0x03});
        header2.setC1(new byte[]{0x00, 0x01, 0x02, 0x03});
        broadcastEncryptionHeaders.put("Header2", header2);
        broadcastEncryptionHeaders.put("Header3", new EncryptionHeaderData());

        Map<String, byte[]> broadcastEncryptedSessionKeys = new HashMap<String, byte[]>();
        broadcastEncryptedSessionKeys.put("Key1", new byte[]{0x00, 0x01, 0x02, 0x03});
        broadcastEncryptedSessionKeys.put("Key2", new byte[]{0x00, 0x01, 0x02, 0x03});

        Map<String, int[]> broadcastEncryptionIds = new HashMap<String, int[]>();
        broadcastEncryptionIds.put("Ids1", new int[]{1, 2, 3});
        broadcastEncryptionIds.put("Ids2", new int[]{5, 6, 7});

        testMessage.setBroadcastEncryptionHeaders(broadcastEncryptionHeaders);
        testMessage.setBroadcastEncryptedSessionKeys(broadcastEncryptedSessionKeys);
        testMessage.setBroadcastEncryptionIds(broadcastEncryptionIds);

        testMessage.setMessageSignature(cryptographyUtil.ecdsaSignature(thingPrivateKey, testMessage));
        assertTrue(cryptographyUtil.checkEcdsaSignature(thingCertificate.getPublicKey(), testMessage));
    }
}
