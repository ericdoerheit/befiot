package de.ericdoerheit.befiot.server;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;

/**
 * Created by ericdorheit on 06/02/16.
 */
public class ServerUtil {

    public static final String TENANT_SERVER_KEY_PREFIX = "tenant-server:";

    private static final SecureRandom secureRandom = new SecureRandom();

    public static String randomToken() {
        String token = "";

        for (int i = 0; i < 10; i++) {
            token += new BigInteger(25, secureRandom).toString(32);
            if (i != 9) {
                token += "-";
            }
        }

        return new String(token);
    }

    public static SSLContext getSSLContext(FileInputStream keyStoreFis, FileInputStream trustStoreFis,
                                           String keyStorePassword, String keyPassword, String trustStorePassword)
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException,
            UnrecoverableKeyException, KeyManagementException {

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(keyStoreFis, keyStorePassword.toCharArray());
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory
                .getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keyPassword.toCharArray());

        KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(trustStoreFis, trustStorePassword.toCharArray());
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory
                .getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), secureRandom);

        return sslContext;
    }
}
