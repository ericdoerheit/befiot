package de.ericdoerheit.efiot.client.test;

import de.ericdoerheit.efiot.client.CryptographyUtil;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.junit.Test;

import javax.crypto.KeyAgreement;
import java.io.FileInputStream;
import java.security.*;
import java.security.cert.Certificate;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by ericdoerheit on 18/04/16.
 */
public class testAsymmetricEncryptionDecryptionWithCertificate {

    public static final String KEYSTORE_PASSWORD = "efiot001";
    public static final String KEYSTORE_LOCATION = "keystore.jks";
    public static final String ALIAS_THING001 = "thing001";
    public static final String ALIAS_THING002 = "thing002";

    @Test
    public void testEcdhWithKeysFromKeystore() throws Exception {
        KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(new FileInputStream(KEYSTORE_LOCATION), KEYSTORE_PASSWORD.toCharArray());

        Certificate certificateThing001 = keystore.getCertificate(ALIAS_THING001);
        assertNotNull(certificateThing001);

        PublicKey publicKeyThing001 = certificateThing001.getPublicKey();
        assertNotNull(publicKeyThing001);

        PrivateKey privateKeyThing001 = (PrivateKey) keystore.getKey(ALIAS_THING001, KEYSTORE_PASSWORD.toCharArray());
        assertNotNull(privateKeyThing001);

        Certificate certificateThing002 = keystore.getCertificate(ALIAS_THING002);
        assertNotNull(certificateThing002);

        PublicKey publicKeyThing002 = certificateThing002.getPublicKey();
        assertNotNull(publicKeyThing002);

        PrivateKey privateKeyThing002 = (PrivateKey) keystore.getKey(ALIAS_THING002, KEYSTORE_PASSWORD.toCharArray());
        assertNotNull(privateKeyThing002);

        Security.addProvider(new BouncyCastleProvider());

        // Thing001 generates secret to encrypt a message (random session key) for Thing002
        try {
            KeyAgreement keyAgreementThing001 = KeyAgreement.getInstance("ECDH", "BC");
            KeyAgreement keyAgreementThing002 = KeyAgreement.getInstance("ECDH", "BC");

            keyAgreementThing001.init(privateKeyThing001);
            keyAgreementThing002.init(privateKeyThing002);

            keyAgreementThing001.doPhase(publicKeyThing002, true);
            keyAgreementThing002.doPhase(publicKeyThing001, true);

            CryptographyUtil cryptographyUtil = new CryptographyUtil();
            byte[] sessionKey = cryptographyUtil.randomKey();
            byte[] iv = cryptographyUtil.randomKey();

            byte[] encryptedSessionKey = cryptographyUtil.aesEncrypt(keyAgreementThing001.generateSecret(),
                    iv, sessionKey, true);

            byte[] decryptedSessionKey = cryptographyUtil.aesDecrypt(keyAgreementThing002.generateSecret(),
                    iv, encryptedSessionKey, true);

            assertArrayEquals(sessionKey, decryptedSessionKey);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
