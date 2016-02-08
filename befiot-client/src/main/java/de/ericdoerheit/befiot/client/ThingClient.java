package de.ericdoerheit.befiot.client;

import de.ericdoerheit.befiot.core.DecryptionKeyAgent;
import de.ericdoerheit.befiot.core.Deserializer;
import de.ericdoerheit.befiot.core.EncryptionKeyAgent;
import de.ericdoerheit.befiot.core.Serializer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.*;

import static de.ericdoerheit.befiot.client.ClientUtil.*;

/**
 * Created by ericdorheit on 06/02/16.
 */
public class ThingClient {
    private final static Logger log = LoggerFactory.getLogger(ThingClient.class);

    public static final String ENCRYPTION_KEY_AGENT_URL = "/encryption-key-agent";
    public static final String TENANT_LIST_URL = "/tenants";
    public static final String DECRYPTION_KEY_AGENT_URL = "/decryption-key-agent";

    private Integer thingId;
    private String thingToken;

    private String tenantServerHost;
    private Integer tenantServerPort;

    private String keyStoreLocation;
    private String keyStorePassword;
    private String keyPassword;

    private String trustStoreLocation;
    private String trustStorePassword;

    private String dataLocation;

    private EncryptionKeyAgent encryptionKeyAgent;

    // Contains DKAs of tenants  (<tenantId, DKA>)
    private Map<String, DecryptionKeyAgent> decryptionKeyAgentMap;

    private OkHttpClient httpClient;

    public ThingClient(Properties properties) {
        decryptionKeyAgentMap = new HashMap<String, DecryptionKeyAgent>();

        thingId = Integer.valueOf(properties.getProperty("thing-id"));
        thingToken = properties.getProperty("thing-token");

        tenantServerHost = properties.getProperty("tenant-server-host");
        tenantServerPort = Integer.valueOf(properties.getProperty("tenant-server-port"));

        keyStoreLocation = properties.getProperty("key-store-location");
        keyStorePassword = properties.getProperty("key-store-password");
        keyPassword = properties.getProperty("key-password");

        trustStoreLocation = properties.getProperty("trust-store-location");
        trustStorePassword = properties.getProperty("trust-store-password");

        dataLocation = properties.getProperty("data-location");

        Object[] mandatoryProperties = new Object[]{tenantServerHost, tenantServerPort, keyStoreLocation, keyStorePassword,
                keyPassword, trustStoreLocation, trustStorePassword};

        boolean mandatoryPropertiesSet = true;
        for (int i = 0; i < mandatoryProperties.length; i++) {
            mandatoryPropertiesSet = mandatoryPropertiesSet && mandatoryProperties[i] == null;
        }
        if (!mandatoryPropertiesSet) {
            log.error("A mandatory property is not set.");
        }
    }

    public void start() {

        SSLContext sslContext = null;
        try {
            sslContext = ClientUtil.getSSLContext(new FileInputStream(keyStoreLocation),
                    new FileInputStream(trustStoreLocation), keyStorePassword, keyPassword, trustStorePassword);
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (IOException e) {
            log.error(e.getMessage());
        } catch (CertificateException e) {
            log.error(e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            log.error(e.getMessage());
        } catch (UnrecoverableKeyException e) {
            log.error(e.getMessage());
        } catch (KeyManagementException e) {
            log.error(e.getMessage());
        }

        httpClient = new OkHttpClient.Builder()
                .sslSocketFactory(sslContext.getSocketFactory())
                .build();

        /* --- Load existing data or create directory for data --- */
        log.debug("Data location: {}.", dataLocation);
        File dataDirectory = new File(dataLocation);

        File thingClientFile = null;
        File encryptionKeyAgentDataFile = null;
        List<File> decryptionKeyAgentDataFiles = new LinkedList<File>();
        if(dataDirectory != null) {
            if (!dataDirectory.exists()) {
                log.debug("Data directory does not exist. It will be created now.");
                if (!dataDirectory.mkdirs()) {
                    log.warn("Data directory could not be created.");
                }
            } else {
                String fileNames = "";
                for (final File file : dataDirectory.listFiles()) {
                    if (!file.isDirectory()) {
                        if (isThingClientFile(file)) {
                            thingClientFile = file;
                        } else if (isEncryptionKeyAgentFile(file)) {
                            encryptionKeyAgentDataFile = file;
                        } else if (isDecryptionKeyAgentFile(file)) {
                            decryptionKeyAgentDataFiles.add(file);
                        }
                        fileNames += file.getName() + " ";
                    }
                    log.debug("Data directory contains: {}.", fileNames);
                }
            }
        }

        /* Maybe such a file will be needed
        if (thingClientFile == null) {
        } else {
        }
        */

        if(encryptionKeyAgentDataFile != null) {
            encryptionKeyAgent = Deserializer.jsonStringToEncryptionKeyAgent(loadFileContentIntoString(encryptionKeyAgentDataFile));
            log.debug("Loaded EKA from file: {}", encryptionKeyAgent);
        } else {
            // Download EKA and store it
            String url = "https://"+tenantServerHost+":"+tenantServerPort+ENCRYPTION_KEY_AGENT_URL;
            Request request = new Request.Builder()
                    .url(url)
                    .build();

            try {
                Response response = httpClient.newCall(request).execute();
                if (200 <= response.code() && response.code() <= 299) {
                    log.debug("Response from request of GET {} Status code: {}", url, response.code());

                    // TODO: 08/02/16 Validate response
                    encryptionKeyAgent = Deserializer.jsonStringToEncryptionKeyAgent(response.body().string());
                    saveStringToFile(dataLocation+"/encryption-key-agent.json",
                            Serializer.encryptionKeyAgentToJsonString(encryptionKeyAgent));
                    
                } else {
                    log.error("Could not receive encryption key agent from {}. Status code: {}", url, response.code());
                }
            } catch (IOException e) {
                log.error("Could not receive encryption key agent from {}. {}", url, e.getMessage());
            }
        }

        // Load DKAs into Map
        for (File file : decryptionKeyAgentDataFiles) {
            String tenantId = getTenantIdFromDkaFileName(file.getName());
            String jsonString = loadFileContentIntoString(file);
            DecryptionKeyAgent decryptionKeyAgent = Deserializer.jsonStringToDecryptionKeyAgent(jsonString);
            decryptionKeyAgentMap.put(tenantId, decryptionKeyAgent);
        }

        // Download list of tenants which provide DKAs from tenant server
        String[] tenantIds = null;
        String url = "https://"+tenantServerHost+":"+tenantServerPort+TENANT_LIST_URL;
        Request request = new Request.Builder()
                .url(url)
                .build();
        try {
            Response response = httpClient.newCall(request).execute();
            if (200 <= response.code() && response.code() <= 299) {
                log.debug("Response from request of GET {} Status code: {}", url, response.code());
                // TODO: 08/02/16 Move body parsing to Core Util class (Deserializer) and use json
                tenantIds = response.body().string().split(",");
            } else {
                log.error("Could not receive list of tenants from {}. Status code: {}", url, response.code());
            }
        } catch (IOException e) {
            log.error("Could not receive list of tenants from {}. {}", url, e.getMessage());
        }

        // Download each new DKA from tenant server and store it in the clients file system
        if (tenantIds != null) {
            for (int i = 0; i < tenantIds.length; i++) {
                if (!decryptionKeyAgentMap.keySet().contains(tenantIds[i])) {
                    String tenantId = tenantIds[i];

                    String dkaUrl = "https://" + tenantServerHost + ":" + tenantServerPort
                            + DECRYPTION_KEY_AGENT_URL + "/" + tenantId + "/" + thingId;
                    Request dkaRequest = new Request.Builder()
                            .url(dkaUrl)
                            .build();
                    try {
                        Response response = httpClient.newCall(dkaRequest).execute();
                        if (200 <= response.code() && response.code() <= 299) {
                            log.debug("Response from request of GET {} Status code: {}", url, response.code());
                            DecryptionKeyAgent decryptionKeyAgent = Deserializer.jsonStringToDecryptionKeyAgent(response.body().string());
                            decryptionKeyAgentMap.put(tenantId, decryptionKeyAgent);
                        } else {
                            log.error("Could not receive DKA from {}. Status code: {}", url, response.code());
                        }
                    } catch (IOException e) {
                        log.error("Could not receive DKA from {}. {}", url, e.getMessage());
                    }
                }
            }
        }

        // TODO: 08/02/16 Periodically check for new DKAs?
    }

    public void stop(){

    }

    public void nextEncryption(int[] ids) {
        encryptionKeyAgent.next(ids);
    }

    public String encryptionHeader() {
        return Serializer.encryptionHeaderToJsonString(encryptionKeyAgent.getHeader());
    }

    public byte[] encryptionKeyBytes() {
        return encryptionKeyAgent.getKeyBytes();
    }

    public byte[] decryptionKeyBytes(String tenantId, String encryptionHeader, int[] ids) {
        return decryptionKeyAgentMap.get(tenantId).getKeyBytes(Deserializer.jsonStringToEncryptionHeader(encryptionHeader), ids);
    }

    public static void main(String[] args) {
        if(args.length < 1) {
            log.error("No configuration file is give.");
            return;
        }

        String propertiesLocation = args[0];

        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(propertiesLocation));
        } catch (IOException e) {
            log.error("Configuration file not found.");
        }

        ThingClient thingClient = new ThingClient(properties);
        thingClient.start();
        thingClient.stop();
    }
}
