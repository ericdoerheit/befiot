package de.ericdoerheit.efiot.client;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

;

/**
 * Created by ericdoerheit on 05/03/16.
 */
public class CryptographyUtil {

    public CryptographyUtil() {
        Security.addProvider(new BouncyCastleProvider());
    }

    public byte[] randomKey() throws NoSuchAlgorithmException {
        SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
        return keyFromHash(secureRandom.generateSeed(16));
    }

    public byte[] keyFromHash(byte[] hash) throws NoSuchAlgorithmException {
        SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
        secureRandom.setSeed(hash);
        byte[] key = new byte[16];
        secureRandom.nextBytes(key);
        return key;
    }

    public byte[] aesEncrypt(byte[] key, byte[] iv, byte[] message, boolean generateKeyFromHash) throws ShortBufferException,
            IllegalBlockSizeException, BadPaddingException, NoSuchAlgorithmException, NoSuchProviderException,
            NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {

        if(generateKeyFromHash) {
            key = keyFromHash(key);
        }

        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding", BouncyCastleProvider.PROVIDER_NAME);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));

        byte[] encryptedMessage = cipher.doFinal(message);

        return encryptedMessage;
    }

    public byte[] aesDecrypt(byte[] key, byte[] iv, byte[] encryptedMessage, boolean generateKeyFromHash)
            throws NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException, InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException {

        if(generateKeyFromHash) {
            key = keyFromHash(key);
        }

        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding", BouncyCastleProvider.PROVIDER_NAME);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));

        byte[] message = cipher.doFinal(encryptedMessage);

        return message;
    }

    public byte[] messageAuthenticationCode(byte[] key, Message message) throws NoSuchAlgorithmException,
            InvalidKeyException, UnsupportedEncodingException {

        Mac mac = Mac.getInstance("HmacSHA1");
        SecretKeySpec signingKey = new SecretKeySpec(key, "HmacSHA1");
        mac.init(signingKey);
        return mac.doFinal(message.macString().getBytes("UTF-8"));
    }

    public boolean checkMessageAuthenticationCode(byte[] key, Message message)
            throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {

        return Arrays.equals(messageAuthenticationCode(key, message), message.getMessageAuthenticationCode());
    }

    public byte[] ecdsaSignature(PrivateKey privateKey, Message message) throws NoSuchProviderException,
            NoSuchAlgorithmException, UnsupportedEncodingException, SignatureException, InvalidKeySpecException,
            InvalidKeyException {

        Signature ecdsaSign = Signature.getInstance("SHA256withECDSA", BouncyCastleProvider.PROVIDER_NAME);
        ecdsaSign.initSign(privateKey);
        ecdsaSign.update(message.signatureString().getBytes("UTF-8"));
        byte[] signature = ecdsaSign.sign();

        return signature;
    }

    public boolean checkEcdsaSignature(PublicKey publicKey, Message message) throws NoSuchProviderException,
            NoSuchAlgorithmException, SignatureException, UnsupportedEncodingException, InvalidKeySpecException,
            InvalidKeyException {

        Signature ecdsaVerify = Signature.getInstance("SHA256withECDSA", BouncyCastleProvider.PROVIDER_NAME);
        ecdsaVerify.initVerify(publicKey);
        ecdsaVerify.update(message.signatureString().getBytes("UTF-8"));
        return ecdsaVerify.verify(message.getMessageSignature());
    }

    public byte[] ecdhKeyAgreement(PrivateKey privateKey, PublicKey publicKey) throws NoSuchProviderException, NoSuchAlgorithmException, InvalidKeyException {
        KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH", "BC");
        keyAgreement.init(privateKey);
        keyAgreement.doPhase(publicKey, true);
        return keyAgreement.generateSecret();
    }

    public static String secureHash(String input) {
        return input;
    }
}
