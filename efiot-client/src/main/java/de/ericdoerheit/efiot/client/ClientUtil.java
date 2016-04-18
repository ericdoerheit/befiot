package de.ericdoerheit.efiot.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Map;

/**
 * Created by ericdorheit on 06/02/16.
 */
public class ClientUtil {
    private final static Logger log = LoggerFactory.getLogger(ClientUtil.class);

    final static Level EVENT = Level.forName("EVENT", 350);
    final static Level MEASUREMENT = Level.forName("MEASUREMENT", 50);
    final static org.apache.logging.log4j.Logger logger = LogManager.getLogger();

    public static final String SESSION_KEY_FILE_NAME_REGEX = "(session-key_).{1,}(.json)";

    private static ObjectMapper objectMapper = new ObjectMapper();

    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public static boolean isSessionKeyFile(File file) {
        return file.getName() != null && file.getName().matches(SESSION_KEY_FILE_NAME_REGEX);
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
            fileOutputStream.close();

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

    protected static void logEvent(String message) {
        logger.log(EVENT, message);
    }

    protected static void logMeasurement(Integer messageSize, Integer encryptionDataSize, Integer decryptionDataSize,
                                         Integer numberReceivers, Integer numberMaxReceivers, Integer tenants) {
        String message = "{"
                + "\"messageSize\": " + messageSize + ","
                + "\"encryptionDataSize\": " + encryptionDataSize + ","
                + "\"decryptionDataSize\": " + decryptionDataSize + ","
                + "\"numberReceivers\": " + numberReceivers + ","
                + "\"numberMaxReceivers\": " + numberMaxReceivers + ","
                + "\"tenants\": " + tenants
                + "}";
        logger.log(MEASUREMENT, message);
    }

    public static String byteMapToString(Map<String, byte[]> map) {
        if(map == null) return null;

        if (map.isEmpty()) {
            return map.toString();
        }

        String result = "[ ";
        for (Map.Entry<String, byte[]> entry : map.entrySet()) {
            result += "{" + entry.getKey() + " : " + Arrays.hashCode(entry.getValue()) + "} ";
        }

        return result + "]";
    }

    public static String intMapToString(Map<String, int[]> map) {
        if(map == null) return null;

        if (map.isEmpty()) {
            return map.toString();
        }

        String result = "[ ";
        for (Map.Entry<String, int[]> entry : map.entrySet()) {
            result += "{" + entry.getKey() + " : " + Arrays.toString(entry.getValue()) + "} ";
        }

        return result + "]";
    }
}
