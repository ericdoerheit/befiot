package de.ericdoerheit.befiot.client;

import de.ericdoerheit.befiot.core.*;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
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

    public static final String DECRYPTION_KEY_AGENT_URL = "/decryption-key-agent";

    public static final String REGISTRY_TENANT_LIST_URL = "/tenants";
    public static final String REGISTRY_ENCRYPTION_KEY_AGENT_URL = "/encryption-key-agent";
    private Integer thingId;
    private String thingToken;

    private String tenantServerHost;
    private Integer tenantServerPort;

    private String tenantRegistryHost;
    private Integer tenantRegistryPort;

    private String keyStoreLocation;
    private String keyStorePassword;
    private String keyPassword;

    private String trustStoreLocation;
    private String trustStorePassword;

    private String dataLocation;

    // Contains EKAs of all tenants  (<tenantId, DKA>)
    private Map<String, EncryptionKeyAgent> encryptionKeyAgentMap;
    private DecryptionKeyAgent decryptionKeyAgent;

    private OkHttpClient httpClient;

    public ThingClient(Properties properties) {
        encryptionKeyAgentMap = new HashMap<String, EncryptionKeyAgent>();

        thingId = Integer.valueOf(properties.getProperty("thing-id"));
        thingToken = properties.getProperty("thing-token");

        tenantServerHost = properties.getProperty("tenant-server-host");
        tenantServerPort = Integer.valueOf(properties.getProperty("tenant-server-port"));

        tenantRegistryHost = properties.getProperty("tenant-registry-host");
        tenantRegistryPort = Integer.valueOf(properties.getProperty("tenant-registry-port"));

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
        logEvent("{\"event\": \"client_started\", \"data\":\""+thingId+"\"}");

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
        File decryptionKeyAgentDataFile = null;
        List<File> encryptionKeyAgentDataFiles = new LinkedList<File>();
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
                        } else if (isDecryptionKeyAgentFile(file)) {
                            decryptionKeyAgentDataFile = file;
                        } else if (isEncryptionKeyAgentFile(file)) {
                            encryptionKeyAgentDataFiles.add(file);
                        }
                        fileNames += file.getName() + " ";
                    }
                    log.debug("Data directory contains: {}.", fileNames);
                }
            }
        }

        if(decryptionKeyAgentDataFile != null) {
            decryptionKeyAgent = Deserializer.jsonStringToDecryptionKeyAgent(loadFileContentIntoString(decryptionKeyAgentDataFile));
            log.debug("Loaded DKA from file: {}", decryptionKeyAgent);
        } else {
            // Download DKA and store it
            String url = "https://"+tenantServerHost+":"+tenantServerPort+DECRYPTION_KEY_AGENT_URL+"/"+thingId;
            Request request = new Request.Builder()
                    .url(url)
                    .build();

            try {
                Response response = httpClient.newCall(request).execute();
                if (200 <= response.code() && response.code() <= 299) {
                    log.debug("Response from request of GET {} Status code: {}", url, response.code());

                    // TODO: 08/02/16 Validate response
                    decryptionKeyAgent = Deserializer.jsonStringToDecryptionKeyAgent(response.body().string());
                    saveStringToFile(dataLocation+File.separator+"decryption-key-agent.json",
                            Serializer.decryptionKeyAgentToJsonString(decryptionKeyAgent));
                    
                } else {
                    log.error("Could not receive decryption key agent from {}. Status code: {}", url, response.code());
                }
                response.body().close();
            } catch (IOException e) {
                log.error("Could not receive decryption key agent from {}. {}", url, e.getMessage());
            }
        }

        // Load EKAs into Map
        for (File file : encryptionKeyAgentDataFiles) {
            String tenantId = getTenantIdFromEkaFileName(file.getName());
            String jsonString = loadFileContentIntoString(file);
            EncryptionKeyAgent encryptionKeyAgent = Deserializer.jsonStringToEncryptionKeyAgent(jsonString);
            encryptionKeyAgentMap.put(tenantId, encryptionKeyAgent);
        }
    }

    private void update() {
        // Check for new tenants and download EKA from tenant registry
        String[] tenantIds = null;
        String url = "https://"+tenantRegistryHost+":"+tenantRegistryPort+REGISTRY_TENANT_LIST_URL;
        Request request = new Request.Builder()
                .url(url)
                .build();
        try {
            Response response = httpClient.newCall(request).execute();
            if (200 <= response.code() && response.code() <= 299) {
                log.debug("Response from request of GET {} Status code: {}", url, response.code());
                // TODO: 08/02/16 Move body parsing to Core Util class (Deserializer) and use json
                tenantIds = response.body().string().replaceAll("\\[|\\]","").split(",");
            } else {
                log.error("Could not receive list of tenants from {}. Status code: {}", url, response.code());
            }
            response.body().close();
        } catch (IOException e) {
            log.error("Could not receive list of tenants from {}. {}", url, e.getMessage());
        }

        // Download each new EKA from registry and store it in the clients file system
        if (tenantIds != null) {
            for (int i = 0; i < tenantIds.length; i++) {
                if (tenantIds[i].length() > 3) {
                    log.debug("Tenant {}.", tenantIds[i]);
                    if (!encryptionKeyAgentMap.keySet().contains(tenantIds[i])) {
                        String tenantId = tenantIds[i];

                        String ekaUrl = "https://" + tenantRegistryHost + ":" + tenantRegistryPort
                                + ENCRYPTION_KEY_AGENT_URL + "/" + tenantId;
                        Request ekaRequest = new Request.Builder()
                                .url(ekaUrl)
                                .build();
                        try {
                            Response response = httpClient.newCall(ekaRequest).execute();
                            String body = response.body().string();
                            if (200 <= response.code() && response.code() <= 299) {
                                log.debug("Response from request of GET {} Status code: {}", ekaUrl, response.code());
                                EncryptionKeyAgent encryptionKeyAgent = Deserializer.jsonStringToEncryptionKeyAgent(body);
                                encryptionKeyAgentMap.put(tenantId, encryptionKeyAgent);
                                saveStringToFile(dataLocation+File.separator+getEkaFileNameFromTenantId(tenantId), body);
                            } else {
                                log.error("Could not receive EKA from {}. Status code: {}", ekaUrl, response.code());
                            }
                        } catch (IOException e) {
                            log.error("Could not receive EKA from {}. {}", ekaUrl, e.getMessage());
                        }
                    }
                }
            }
        }
    }

    public void stop(){
        logEvent("{\"event\": \"client_stopped\", \"data\":\""+thingId+"\"}");
    }

    public void nextEncryption(String tenantId, int[] ids) {
        logEvent("{\"event\": \"encrypt_data\", \"data\":\""+thingId+"\"}");

        EncryptionKeyAgent encryptionKeyAgent = encryptionKeyAgentMap.get(tenantId);

        if (encryptionKeyAgent == null) {
            update();
            encryptionKeyAgent = encryptionKeyAgentMap.get(tenantId);
        }

        if (encryptionKeyAgent == null) {
            log.error("Encryption key agent not available for tenant with id {}", tenantId);
            return;
        } else {
            encryptionKeyAgentMap.get(tenantId).next(ids);
        }
    }

    public String encryptionHeader(String tenantId) {
        EncryptionKeyAgent encryptionKeyAgent = encryptionKeyAgentMap.get(tenantId);

        if (encryptionKeyAgent == null) {
            log.error("Encryption key agent not available for tenant with id {}", tenantId);
            return null;
        }

        if (encryptionKeyAgent.getHeader() != null) {
            return Serializer.encryptionHeaderToJsonString(encryptionKeyAgentMap.get(tenantId).getHeader());
        }

        return null;

    }

    public byte[] encryptionKeyBytes(String tenantId) {EncryptionKeyAgent encryptionKeyAgent = encryptionKeyAgentMap.get(tenantId);

        if (encryptionKeyAgent == null) {
            log.error("Encryption key agent not available for tenant with id {}", tenantId);
            return null;
        }

        return encryptionKeyAgentMap.get(tenantId).getKeyBytes();
    }

    public byte[] decryptionKeyBytes(String tenantId, String encryptionHeader, int[] ids) {
        logEvent("{\"event\": \"decrypt_data\", \"data\":\""+thingId+"\"}");

        if (decryptionKeyAgent == null) {
            log.error("Decryption key agent not available for tenant with id {}", tenantId);
            return null;
        }
        return decryptionKeyAgent.getKeyBytes(Deserializer.jsonStringToEncryptionHeader(encryptionHeader), ids);
    }

    public String getTenantId() {
        return Util.tenantId(tenantServerHost, tenantServerPort);
    }

    public Integer getThingId() {
        return thingId;
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

        final ThingClient thingClient = new ThingClient(properties);

        Runtime.getRuntime().addShutdownHook( new Thread() {
            @Override public void run() {
                log.info("Stop thing client.");
                thingClient.stop();
            }
        } );

        thingClient.start();
        thingClient.nextEncryption(thingClient.getTenantId(), new int[]{thingClient.getThingId()});
        String header = thingClient.encryptionHeader(thingClient.getTenantId());
        byte[] encryptionKeyBytes = thingClient.encryptionKeyBytes(thingClient.getTenantId());
        byte[] decryptionKeyBytes = thingClient.decryptionKeyBytes(thingClient.getTenantId(), header, new int[]{thingClient.getThingId()});

        log.info("Equal: {}", Arrays.equals(encryptionKeyBytes, decryptionKeyBytes));
    }
}
