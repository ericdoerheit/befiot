package de.ericdoerheit.befiot.client;

import de.ericdoerheit.befiot.core.DecryptionKeyAgent;
import de.ericdoerheit.befiot.core.EncryptionKeyAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Map;

/**
 * Created by ericdorheit on 06/02/16.
 */
public class ClientUtil {
    private final static Logger log = LoggerFactory.getLogger(ClientUtil.class);

    public static final String THING_CLIENT_FILE_NAME_REGEX = "thing-client.json";
    public static final String ENCRYPTION_KEY_AGENT_FILE_NAME_REGEX = "encryption-key-agent.json";
    public static final String DECRYPTION_KEY_AGENT_FILE_NAME_REGEX = "(decryption-key-agent_).{1,}(.json)";

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

        SecureRandom secureRandom = new SecureRandom();
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), secureRandom);

        return sslContext;
    }

    public static boolean isThingClientFile(File file) {
        return file.getName() != null && file.getName().matches(THING_CLIENT_FILE_NAME_REGEX);
    }

    public static boolean isEncryptionKeyAgentFile(File file) {
        return file.getName() != null && file.getName().matches(ENCRYPTION_KEY_AGENT_FILE_NAME_REGEX);
    }

    public static boolean isDecryptionKeyAgentFile(File file) {
        return file.getName() != null && file.getName().matches(DECRYPTION_KEY_AGENT_FILE_NAME_REGEX);
    }

    public static String loadFileContentIntoString(File file) {
        try {
            byte[] encoded = Files.readAllBytes(file.toPath());
            return new String(encoded, "UTF-8");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
        return null;
    }

    public static boolean saveStringToFile(String path, String content) {

        File file = new File(path);

        try {
            if (!file.exists()) {
                if(!file.createNewFile()) {
                    return false;
                }
            }
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(content.getBytes("UTF-8"));

        } catch (FileNotFoundException e) {
            log.error(e.getMessage());
            return false;
        } catch (UnsupportedEncodingException e) {
            log.error(e.getMessage());
            return false;
        } catch (IOException e) {
            log.error(e.getMessage());
            return false;
        }
        return true;
    }

    public static String getTenantIdFromDkaFileName(String fileName) {
        String result = fileName.replaceAll("decryption-key-agent_|.json", "");
        return result;
    }

    public static String getDkaFileNameFromTenantId(String tenantId) {
        String result = "decryption-key-agent_"+tenantId+".json";
        return result;
    }
}
