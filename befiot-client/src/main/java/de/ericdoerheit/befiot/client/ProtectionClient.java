package de.ericdoerheit.befiot.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.ericdoerheit.befiot.core.*;
import de.ericdoerheit.befiot.core.data.EncryptionHeaderData;
import de.ericdoerheit.befiot.core.data.EncryptionKeyAgentData;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.util.*;

import static de.ericdoerheit.befiot.client.ClientUtil.*;

/**
 * Created by ericdorheit on 06/02/16.
 */
public class ProtectionClient implements IProtectionClient {
    private final static Logger log = LoggerFactory.getLogger(ProtectionClient.class);

    public static final String ENCRYPTION_KEY_AGENT_URL = "/encryption-key-agent";
    public static final String DECRYPTION_KEY_AGENT_URL = "/decryption-key-agent";
    public static final String REGISTRY_TENANT_LIST_URL = "/tenants";
    public static final String REGISTRY_ENCRYPTION_KEY_AGENT_URL = "/encryption-key-agent";

    public static final long SESSION_KEY_VALIDITY = 86400000; // Session key is valid for one day

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

    private Map<Integer, SessionKey> encryptionSessionKeys;
    private Map<Integer, SessionKey> decryptionSessionKeys;


    private OkHttpClient httpClient;

    private CryptographyUtil cryptographyUtil;

    private Certificate thingCertificate;
    private PrivateKey thingPrivateKey;

    public ProtectionClient(Properties properties) {
        encryptionKeyAgentMap = new HashMap<String, EncryptionKeyAgent>();
        encryptionSessionKeys = new HashMap<Integer, SessionKey>();
        decryptionSessionKeys = new HashMap<Integer, SessionKey>();

        cryptographyUtil = new CryptographyUtil();

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
            mandatoryPropertiesSet = mandatoryPropertiesSet && mandatoryProperties[i] != null;
        }

        if (!mandatoryPropertiesSet) {
            log.error("A mandatory property is not set.");
        }
    }

    public void start() {
        logEvent("{\"event\": \"client_started\", \"data\":\""+getFullThingId()+"\"}");
        log.info("Start client...");
        // TODO if(first start) -> generate keystore and upload to tenant and get signed certificate (use claiming token for first authentication)

        SSLContext sslContext = null;
        try {

            FileInputStream fisKeyStore = new FileInputStream(keyStoreLocation);
            FileInputStream fisTrustStore = new FileInputStream(trustStoreLocation);
            sslContext = ClientUtil.getSSLContext(fisKeyStore,
                    fisTrustStore, keyStorePassword, keyPassword, trustStorePassword);

            fisKeyStore.close();
            fisTrustStore.close();
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

        try {
            KeyStore keystore = KeyStore.getInstance("JKS");
            keystore.load(new FileInputStream(keyStoreLocation), keyStorePassword.toCharArray());
            thingCertificate = keystore.getCertificate(getFullThingId());

            if (thingCertificate == null) {
                log.error("No certificate found!");
            }

            thingPrivateKey = (PrivateKey) keystore.getKey(getFullThingId(), keyPassword.toCharArray());

        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        }

        /* --- Load existing data or create directory for data --- */
        log.debug("Data location: {}.", dataLocation);
        File dataDirectory = new File(dataLocation);

        File decryptionKeyAgentDataFile = null;
        List<File> encryptionKeyAgentDataFiles = new LinkedList<File>();
        List<File> sessionKeyDataFiles = new LinkedList<File>();
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
                        if (isDecryptionKeyAgentFile(file)) {
                            decryptionKeyAgentDataFile = file;
                        } else if (isEncryptionKeyAgentFile(file)) {
                            encryptionKeyAgentDataFiles.add(file);
                        } else if (isSessionKeyFile(file)) {
                            sessionKeyDataFiles.add(file);
                        }
                        fileNames += file.getName() + " ";
                    }
                    log.debug("Data directory contains: {}.", fileNames);
                }
            }
        }

        if(decryptionKeyAgentDataFile != null) {
            DecryptionKeyAgent existingDecryptionKeyAgent = Deserializer
                    .jsonStringToDecryptionKeyAgent(loadFileContentIntoString(decryptionKeyAgentDataFile));

            if (existingDecryptionKeyAgent.validate(System.currentTimeMillis())
                    && existingDecryptionKeyAgent.getId() == thingId) {
                decryptionKeyAgent = existingDecryptionKeyAgent;
            } else {
                decryptionKeyAgentDataFile.delete();
                decryptionKeyAgentDataFile = null;
            }

            log.debug("Load DKA from file: {}", decryptionKeyAgent);
        }

        if (decryptionKeyAgent == null) {
            // Download DKA and store it
            // TODO RETRIES?
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

            if (encryptionKeyAgent.validate(System.currentTimeMillis())) {
                encryptionKeyAgentMap.put(tenantId, encryptionKeyAgent);
            } else {
                file.delete();
            }
        }

        // Load session keys into Map
        for (File file : sessionKeyDataFiles) {
            SessionKey sessionKey;
            try {
                sessionKey = getObjectMapper().readValue(loadFileContentIntoString(file), SessionKey.class);

                if (sessionKey.validate(System.currentTimeMillis())) {
                    encryptionSessionKeys.put(sessionKey.getSessionId(), sessionKey);
                } else {
                    file.delete();
                }
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        }
    }

    /**
     * Check if all tenant encryption key agents are updated and update if necessary
     * @param tenantIds
     */
    private int checkAndUpdate(Collection<String> tenantIds) {
        /*
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
                // optional: Move body parsing to Core Util class (Deserializer) and use json
                tenantIds = response.body().string().replaceAll("\\[|\\]","").split(",");
            } else {
                log.error("Could not receive list of tenants from {}. Status code: {}", url, response.code());
            }
            response.body().close();
        } catch (IOException e) {
            log.error("Could not receive list of tenants from {}. {}", url, e.getMessage());
        }
        */

        // Download each new EKA from registry and store it in the clients file system
        if (tenantIds != null) {
            int outdatedEncryptionKeyAgents = tenantIds.size();
            for (String tenantId : tenantIds) {
                log.debug("Tenant {}.", tenantId);

                EncryptionKeyAgent existingEncryptionKeyAgent = encryptionKeyAgentMap.get(tenantId);

                if (!(existingEncryptionKeyAgent != null
                        && existingEncryptionKeyAgent.validate(System.currentTimeMillis()))) {

                    if (existingEncryptionKeyAgent != null) {
                        encryptionKeyAgentMap.remove(existingEncryptionKeyAgent);
                    }

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
                            EncryptionKeyAgentData encryptionKeyAgentData = Deserializer
                                    .jsonStringToEncryptionKeyAgentData(body);

                            if(validateEncryptionKeyAgentDataSignature(encryptionKeyAgentData)) {
                                EncryptionKeyAgent encryptionKeyAgent = Deserializer
                                        .encryptionKeyAgentFromEncryptionKeyAgentData(encryptionKeyAgentData);

                                if (encryptionKeyAgent.validate(System.currentTimeMillis())) {
                                    encryptionKeyAgentMap.put(tenantId, encryptionKeyAgent);
                                    saveStringToFile(dataLocation + File.separator
                                            + getEkaFileNameFromTenantId(tenantId), body);
                                    outdatedEncryptionKeyAgents--;
                                }
                            }
                        } else {
                            log.error("Could not receive EKA from {}. Status code: {}", ekaUrl, response.code());
                        }
                    } catch (IOException e) {
                        log.error("Could not receive EKA from {}. {}", ekaUrl, e.getMessage());
                    }
                }
            }
            return outdatedEncryptionKeyAgents;
        }

        return 0;
    }

    private boolean validateEncryptionKeyAgentDataSignature(EncryptionKeyAgentData encryptionKeyAgentData) {
        // TODO
        return true;
    }

    public void stop(){
        logEvent("{\"event\": \"client_stopped\", \"data\":\""+getFullThingId()+"\"}");
    }

    public void nextEncryption(String tenantId, int[] ids) {
        EncryptionKeyAgent encryptionKeyAgent = encryptionKeyAgentMap.get(tenantId);

        if (encryptionKeyAgent == null) {

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

    public String getFullThingId() {
        return thingId + "@" + getTenantId();
    }

    @Override
    public Message protectMessage(byte[] message, long notValidAfter, Map<String, Set<String>> receivers, boolean forceNewSessionKey) {
        logEvent("{\"event\": \"protect_message\", \"data\":\""+getFullThingId()+"\"}");
        if(receivers.isEmpty()) {
            log.debug("Receiver set is empty");
            return null;
        }

        int encryptionDataSize = 0;
        int numberReceivers = 0;
        int numberMaxReceivers = decryptionKeyAgent.getPublicKey().size() / 2 - 1;
        int tenants = 0;

        long timestamp = System.currentTimeMillis();

        int hash = receivers.hashCode();

        SessionKey sessionKey = encryptionSessionKeys.get(hash);

        Message protectedMessage = new Message();
        protectedMessage.setSessionId(hash);
        protectedMessage.setTenantId(getTenantId());
        protectedMessage.setThingId(String.valueOf(thingId));
        protectedMessage.setTimestamp(timestamp);
        protectedMessage.setNotValidAfter(notValidAfter);

        try {
            protectedMessage.setIv(cryptographyUtil.randomKey());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        boolean newSessionKey = forceNewSessionKey || sessionKey == null || (sessionKey != null && !sessionKey.validate(timestamp));

        if (newSessionKey) {
            // Create new session key
            byte[] key = new byte[0];
            try {
                key = cryptographyUtil.randomKey();
            } catch (NoSuchAlgorithmException e) {
                logEvent("{\"event\": \"protect_message_error\", \"data\":\""+getFullThingId()+"\"}");
                e.printStackTrace();
            }
            sessionKey = new SessionKey();
            sessionKey.setSessionId(hash);
            sessionKey.setSessionKey(key);
            sessionKey.setNotValidAfter(timestamp + SESSION_KEY_VALIDITY);
            encryptionSessionKeys.put(hash, sessionKey);
            // TODO persist session key

            checkAndUpdate(receivers.keySet());
            // TODO Ignore unavailable tenants (store key for different session id (group hash) and delete old/invalid one)

            Map<String, EncryptionHeaderData> broadcastEncryptionHeaders = new HashMap<String, EncryptionHeaderData>();
            Map<String, byte[]> broadcastEncryptedSessionKeys = new HashMap<String, byte[]>();
            Map<String, int[]> broadcastEncryptionIds = new HashMap<String, int[]>();

            log.debug("Receivers: {}", receivers);
            for (Map.Entry<String, Set<String>> entry : receivers.entrySet()) {
                // For simplicity each thingId is an integer that is equal to the BE system id

                String tenantId = entry.getKey();
                Set<String> receiverIds = entry.getValue();

                EncryptionKeyAgent encryptionKeyAgent = encryptionKeyAgentMap.get(tenantId);

                int[] ids = new int[receiverIds.size()];

                int i = 0;
                for (String id : receiverIds) {
                    ids[i] = Integer.valueOf(id);
                    i++;
                }

                tenants++;
                numberReceivers += ids.length;
                encryptionDataSize += Serializer.encryptionKeyAgentToJsonString(encryptionKeyAgent).getBytes().length;

                if (encryptionKeyAgent != null) {
                    broadcastEncryptionIds.put(tenantId, ids);

                    encryptionKeyAgent.next(ids);
                    broadcastEncryptionHeaders.put(tenantId,
                            Serializer.encryptionHeaderToEncryptionKeyHeaderData(encryptionKeyAgent.getHeader()));

                    byte[] beKey = encryptionKeyAgent.getKeyBytes();
                    byte[] encryptedSessionKey = new byte[0];
                    try {
                        encryptedSessionKey = cryptographyUtil.aesEncrypt(beKey, protectedMessage.getIv(),
                                sessionKey.getSessionKey(), true);
                    } catch (Exception e) {
                        e.printStackTrace();
                        logEvent("{\"event\": \"protect_message_error\", \"data\":\""+getFullThingId()+"\"}");
                    }
                    broadcastEncryptedSessionKeys.put(tenantId, encryptedSessionKey);
                } else {
                    logEvent("{\"event\": \"protect_message_error\", \"data\":\""+getFullThingId()+"\"}");
                    log.warn("Encryption key agent is null");
                }
            }

            protectedMessage.setSessionKeyNotValidAfter(timestamp + SESSION_KEY_VALIDITY);
            protectedMessage.setBroadcastEncryptionHeaders(broadcastEncryptionHeaders);
            protectedMessage.setBroadcastEncryptedSessionKeys(broadcastEncryptedSessionKeys);
            protectedMessage.setBroadcastEncryptionIds(broadcastEncryptionIds);
            try {
                protectedMessage.setThingCertificate(thingCertificate.getEncoded());
            } catch (CertificateEncodingException e) {
                e.printStackTrace();
                logEvent("{\"event\": \"protect_message_error\", \"data\":\""+getFullThingId()+"\"}");
            }
        }

        // Encrypt message
        try {
            protectedMessage.setEncryptedMessage(cryptographyUtil.aesEncrypt(sessionKey.getSessionKey(),
                    protectedMessage.getIv(), message, false));
        } catch (Exception e) {
            e.printStackTrace();
            logEvent("{\"event\": \"protect_message_error\", \"data\":\""+getFullThingId()+"\"}");
        }
        // Create MAC of message (of all mandatory fields)
        try {
            protectedMessage.setMessageAuthenticationCode(cryptographyUtil.messageAuthenticationCode(sessionKey.getSessionKey(), protectedMessage));
        } catch (Exception e) {
            e.printStackTrace();
            logEvent("{\"event\": \"protect_message_error\", \"data\":\""+getFullThingId()+"\"}");
        }

        if(newSessionKey) {
            // Create signature of message (of all fields except signature)
            try {
                protectedMessage.setMessageSignature(cryptographyUtil.ecdsaSignature(thingPrivateKey, protectedMessage));
            } catch (Exception e) {
                e.printStackTrace();
                logEvent("{\"event\": \"protect_message_error\", \"data\":\""+getFullThingId()+"\"}");
            }
        }

        logEvent("{\"event\": \"protect_message_success\", \"data\":\""+getFullThingId()+"\"}");

        try {
            String protectedMessageAsJsonString = getObjectMapper().writeValueAsString(protectedMessage);
            logMeasurement(protectedMessageAsJsonString.length(), encryptionDataSize, null, numberReceivers,
                    numberMaxReceivers, tenants);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return protectedMessage;
    }

    @Override
    public Message protectMessage(byte[] message, long notValidAfter, Set<String> receivers, boolean forceNewSessionKey) {
        Map<String, Set<String>> receiverMap = new HashMap<String, Set<String>>();

        for (String fullThingId : receivers) {
            String[] parts = fullThingId.split("@");

            if (parts != null && parts.length > 1) {
                String thingId = parts[0];
                String tenantId = parts[1];

                Set<String> tenantReceivers = receiverMap.get(tenantId);

                if (tenantReceivers == null) {
                    tenantReceivers = new HashSet<String>();
                    receiverMap.put(tenantId, tenantReceivers);
                }

                tenantReceivers.add(thingId);
            }
        }

        log.debug("{}", receiverMap);
        return protectMessage(message, notValidAfter, receiverMap, forceNewSessionKey);
    }

    @Override
    public byte[] retrieveMessage(Message protectedMessage, boolean sessionKeyIsNew) {
        logEvent("{\"event\": \"retrieve_message\", \"data\":\""+getFullThingId()+"\"}");
        long timestamp = System.currentTimeMillis();

        int decryptionDataSize = Serializer.decryptionKeyAgentToJsonString(decryptionKeyAgent).getBytes().length;

        int numberReceivers = 0;
        for(Map.Entry<String, int[]> entry : protectedMessage.getBroadcastEncryptionIds().entrySet()) {
            numberReceivers += entry.getValue().length;
        }

        int numberMaxReceivers = decryptionKeyAgent.getPublicKey().size() / 2 - 1;
        int tenants = protectedMessage.getBroadcastEncryptedSessionKeys().size();

        if(protectedMessage != null && protectedMessage.validate(timestamp)) {
            SessionKey sessionKey = decryptionSessionKeys.get(protectedMessage.getSessionId());

            if ((sessionKey == null || (sessionKey != null && !sessionKey.validate(timestamp)) || sessionKeyIsNew)
                    && protectedMessage.hasOptionals()) {
                // Get session key from optional data

                try {

                    CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509", "BC");
                    InputStream in = new ByteArrayInputStream(protectedMessage.getThingCertificate());
                    X509Certificate certificate = (X509Certificate)certificateFactory.generateCertificate(in);
                    // TODO: 06/03/16 Validate / check certificate (Signature, CRL)

                    if(!cryptographyUtil.checkEcdsaSignature(certificate.getPublicKey(), protectedMessage)) {
                        // Signature is invalid
                        log.warn("Signature is invalid");
                        logEvent("{\"event\": \"retrieve_message_error\", \"data\":\""+getFullThingId()+"\"}");
                        return null;
                    }
                } catch (Exception e) {
                   e.printStackTrace();
                }

                byte[] encryptedSessionKey = protectedMessage.getBroadcastEncryptedSessionKeys().get(getTenantId());
                EncryptionHeaderData headerData = protectedMessage.getBroadcastEncryptionHeaders().get(getTenantId());
                int[] ids = protectedMessage.getBroadcastEncryptionIds().get(getTenantId());

                if(encryptedSessionKey != null && headerData != null && ids != null) {
                    log.debug("Header Data: {}, IDs: {}, DKA: {}", headerData.toString(), Arrays.toString(ids), decryptionKeyAgent.toString());
                    byte[] beKey = decryptionKeyAgent.getKeyBytes(Deserializer.encryptionHeaderFromEncryptionHeaderData(headerData), ids);
                    byte[] key = new byte[0];

                    log.debug("BE Key: {}", Arrays.hashCode(beKey));

                    try {
                        key = cryptographyUtil.aesDecrypt(beKey, protectedMessage.getIv(), encryptedSessionKey, true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    sessionKey = new SessionKey();
                    sessionKey.setSessionId(protectedMessage.getSessionId());
                    sessionKey.setNotValidAfter(protectedMessage.getSessionKeyNotValidAfter());
                    sessionKey.setSessionKey(key);
                    decryptionSessionKeys.put(sessionKey.getSessionId(), sessionKey);

                } else {
                    // Message is not encrypted for this tenant
                    log.warn("Message is not encrypted for this tenant. Encrypted session key: {} Header Data: {} IDs: {}",
                            encryptedSessionKey!=null?encryptedSessionKey.hashCode():encryptedSessionKey, headerData, Arrays.toString(ids));
                    logEvent("{\"event\": \"retrieve_message_error\", \"data\":\""+getFullThingId()+"\"}");
                    return null;
                }
            } else {
                log.warn("Session key is null or invalid");
            }

            // Check MAC
            try {
                if(sessionKey != null && cryptographyUtil.checkMessageAuthenticationCode(sessionKey.getSessionKey(), protectedMessage)) {
                    // MAC is valid
                    // Decrypt
                    log.debug("Decrypt message");
                    logEvent("{\"event\": \"retrieve_message_success\", \"data\":\""+getFullThingId()+"\"}");
                    try {
                        String protectedMessageAsJsonString = getObjectMapper().writeValueAsString(protectedMessage);
                        logMeasurement(protectedMessageAsJsonString.length(), null, decryptionDataSize, numberReceivers,
                                numberMaxReceivers, tenants);
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                    return cryptographyUtil.aesDecrypt(sessionKey.getSessionKey(), protectedMessage.getIv(), protectedMessage.getEncryptedMessage(), false);
                } else {
                    log.warn("session key is null or MAC is not valid");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            log.warn("Timestamp is invalid");
        }

        // Message invalid
        log.warn("Message is invalid");
        logEvent("{\"event\": \"retrieve_message_error\", \"data\":\""+getFullThingId()+"\"}");

        return null;
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

        final ProtectionClient thingClient = new ProtectionClient(properties);

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
