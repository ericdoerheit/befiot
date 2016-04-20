package de.ericdoerheit.efiot.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.net.ssl.*;
import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.util.*;

import static de.ericdoerheit.efiot.client.ClientUtil.*;

/**
 * Created by ericdorheit on 06/02/16.
 */
public class ProtectionClient implements IProtectionClient {
    private final static Logger log = LoggerFactory.getLogger(ProtectionClient.class);

    public static final long SESSION_KEY_VALIDITY = 86400000; // Session key is valid for one day

    private String thingId;

    private String registryUrl;

    private String keyStoreLocation;
    private String keyStorePassword;
    private String keyPassword;

    private String receiverTrustStoreLocation;
    private String receiverTrustStorePassword;

    // Not used yet, will be used for root certificates to verify thing certificates
    private String trustStoreLocation;
    private String trustStorePassword;

    private String dataLocation;

    private Map<Integer, SessionKey> encryptionSessionKeys;
    private Map<Integer, SessionKey> decryptionSessionKeys;

    private OkHttpClient httpClient;

    private CryptographyUtil cryptographyUtil;

    private Certificate thingCertificate;
    private PrivateKey thingPrivateKey;

    private KeyStore receiverTrustStore;

    public ProtectionClient(Properties properties) {
        encryptionSessionKeys = new HashMap<Integer, SessionKey>();
        decryptionSessionKeys = new HashMap<Integer, SessionKey>();

        cryptographyUtil = new CryptographyUtil();

        thingId = properties.getProperty("thing-id");

        registryUrl = properties.getProperty("registry-url");

        keyStoreLocation = properties.getProperty("key-store-location");
        keyStorePassword = properties.getProperty("key-store-password");
        keyPassword = properties.getProperty("key-password");

        receiverTrustStoreLocation = properties.getProperty("receiver-trust-store-location");
        receiverTrustStorePassword = properties.getProperty("receiver-trust-store-password");

        dataLocation = properties.getProperty("data-location");

        Object[] mandatoryProperties = new Object[]{thingId, registryUrl, keyStoreLocation, keyStorePassword,
                keyPassword, receiverTrustStoreLocation, receiverTrustStorePassword, dataLocation};

        boolean mandatoryPropertiesSet = true;
        for (int i = 0; i < mandatoryProperties.length; i++) {
            mandatoryPropertiesSet = mandatoryPropertiesSet && mandatoryProperties[i] != null;
        }

        if (!mandatoryPropertiesSet) {
            log.error("A mandatory property is not set.");
        }
    }

    public void start() {
        logEvent("{\"event\": \"client_started\", \"data\":\""+thingId+"\"}");
        log.info("Start client...");

        httpClient = ClientUtil.getDefaultHttpClient();

        try {
            KeyStore keystore = KeyStore.getInstance("JKS");
            keystore.load(new FileInputStream(keyStoreLocation), keyStorePassword.toCharArray());
            thingCertificate = keystore.getCertificate(thingId);

            receiverTrustStore = KeyStore.getInstance("JKS");
            receiverTrustStore.load(new FileInputStream(receiverTrustStoreLocation), receiverTrustStorePassword.toCharArray());

            if (thingCertificate == null) {
                log.error("No certificate found!");
            }

            thingPrivateKey = (PrivateKey) keystore.getKey(thingId, keyPassword.toCharArray());

        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException
                | UnrecoverableKeyException e) {
            e.printStackTrace();
        }

        /* --- Load existing data or create directory for data --- */
        log.debug("Data location: {}.", dataLocation);
        File dataDirectory = new File(dataLocation);

        List<File> sessionKeyDataFiles = new LinkedList<File>();
        if (!dataDirectory.exists()) {
            log.debug("Data directory does not exist. It will be created now.");
            if (!dataDirectory.mkdirs()) {
                log.warn("Data directory could not be created.");
            }
        } else {
            String fileNames = "";
            for (final File file : dataDirectory.listFiles()) {
                if (!file.isDirectory()) {
                    if (isSessionKeyFile(file)) {
                        sessionKeyDataFiles.add(file);
                    }fileNames += file.getName() + " ";
                }
                log.debug("Data directory contains: {}.", fileNames);
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


        // Upload certificate to registry
        String uploadCertificateUrl = registryUrl + "/" + thingId;
        try {
            String encodedCertificate = Base64.getEncoder().encodeToString(thingCertificate.getEncoded());
            RequestBody uploadCertificateBody = RequestBody.create(MediaType.parse("text/plain; charset=utf-8"),
                    encodedCertificate);
            Request uploadCertificateRequest = new Request.Builder().url(uploadCertificateUrl).post(uploadCertificateBody).build();
            log.debug("Request to {}.", uploadCertificateUrl);
            Response response = httpClient.newCall(uploadCertificateRequest).execute();
            String body = response.body().string();

            if (200 <= response.code() && response.code() <= 299) {
                log.debug("Response from request of POST {} Status code: {}", uploadCertificateUrl, response.code());
                log.info("Certificate successfully uploaded to registry.");

            } else {
                log.error("Could not upload certificate to registry ({}). Status code: {}", uploadCertificateUrl, response.code());
            }

        } catch (IOException e) {
            log.error("Could not receive tenant data from {}. {}", uploadCertificateUrl, e.getMessage());
        } catch (CertificateEncodingException e) {
            log.error("Could not encode certificate.");
        }
    }

    private Certificate downloadCertificateFromRegistry(String receiverId) throws KeyStoreException {
        String url = registryUrl + "/" + receiverId;
        Request request = new Request.Builder().url(url).build();

        try {
            Response response = httpClient.newCall(request).execute();
            if (200 <= response.code() && response.code() <= 299) {
                log.debug("Response from request of GET {} Status code: {}", url, response.code());

                // TODO Validate certificate signature <- Is it signed by an root certificate of the trust store?
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                byte[] body = Base64.getDecoder().decode(response.body().string());
                InputStream in = new ByteArrayInputStream(body);
                Certificate certificate = certificateFactory.generateCertificate(in);
                receiverTrustStore.setCertificateEntry(receiverId, certificate);
                response.body().close();
                return certificate;
            } else {
                log.error("Could not receive certificate from {}. Status code: {}", url, response.code());
            }
            response.body().close();
        } catch (IOException e) {
            log.error("Could not receive certificate from {}. Error: {}", url, e.getMessage());
        } catch (CertificateException e) {
            log.error(e.getMessage());
        }
        return null;
    }

    public void stop(){
        logEvent("{\"event\": \"client_stopped\", \"data\":\""+thingId+"\"}");
    }

    public String getThingId() {
        return thingId;
    }


    @Override
    public Message protectMessage(byte[] message, long notValidAfter, Set<String> receivers, boolean forceNewSessionKey) {
        logEvent("{\"event\": \"protect_message\", \"data\":\""+thingId+"\"}");

        if(receivers.isEmpty()) {
            log.debug("Receiver set is empty");
            return null;
        }

        int encryptionDataSize = 0;
        int numberReceivers = receivers.size();
        int numberMaxReceivers = 0;
        int tenants = 0;

        long timestamp = System.currentTimeMillis();

        int hash = receivers.hashCode();

        SessionKey sessionKey = encryptionSessionKeys.get(hash);

        Message protectedMessage = new Message();
        protectedMessage.setSessionId(hash);
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
                logEvent("{\"event\": \"protect_message_error\", \"data\":\""+thingId+"\"}");
                e.printStackTrace();
            }
            sessionKey = new SessionKey();
            sessionKey.setSessionId(hash);
            sessionKey.setSessionKey(key);
            sessionKey.setNotValidAfter(timestamp + SESSION_KEY_VALIDITY);
            encryptionSessionKeys.put(hash, sessionKey);

            // TODO persist session key

            Map<String, byte[]> encryptedSessionKeys = new HashMap<String, byte[]>();

            log.debug("Receivers: {}", receivers);
            for (String receiver : receivers) {

                try {
                    Certificate certificate = receiverTrustStore.getCertificate(receiver);

                    if(certificate == null) {
                        certificate = downloadCertificateFromRegistry(receiver);
                    }

                    if(certificate != null) {
                        encryptionDataSize += certificate.getEncoded().length;

                        try {
                            byte[] ecdhKey = cryptographyUtil.ecdhKeyAgreement(thingPrivateKey,
                                    certificate.getPublicKey());

                            byte[] encryptedSessionKey = cryptographyUtil.aesEncrypt(ecdhKey,
                                    protectedMessage.getIv(), sessionKey.getSessionKey(), true);
                            encryptedSessionKeys.put(CryptographyUtil.secureHash(receiver), encryptedSessionKey);

                        } catch (NoSuchProviderException | NoSuchAlgorithmException | InvalidKeyException e) {
                            log.warn("Error while performing ECDH key agreement: {}", e.getMessage());
                        } catch (ShortBufferException | InvalidAlgorithmParameterException | BadPaddingException
                                | IllegalBlockSizeException | NoSuchPaddingException e) {
                            log.warn("Error while performing AES encryption with key from ECDH key agreement: {}",
                                    e.getMessage());
                        }

                    } else {
                        log.warn("No certificate for receiver with id: {}", receiver);
                    }
                } catch (KeyStoreException e) {
                    e.printStackTrace();
                } catch (CertificateEncodingException e) {
                    e.printStackTrace();
                }
            }

            protectedMessage.setSessionKeyNotValidAfter(timestamp + SESSION_KEY_VALIDITY);
            protectedMessage.setEncryptedSessionKeys(encryptedSessionKeys);

            try {
                protectedMessage.setThingCertificate(thingCertificate.getEncoded());
            } catch (CertificateEncodingException e) {
                e.printStackTrace();
                logEvent("{\"event\": \"protect_message_error\", \"data\":\""+thingId+"\"}");
            }
        }

        // Encrypt message
        try {
            protectedMessage.setEncryptedMessage(cryptographyUtil.aesEncrypt(sessionKey.getSessionKey(),
                    protectedMessage.getIv(), message, false));
        } catch (Exception e) {
            e.printStackTrace();
            logEvent("{\"event\": \"protect_message_error\", \"data\":\""+thingId+"\"}");
        }

        // Create MAC of message (of all mandatory fields)
        try {
            protectedMessage.setMessageAuthenticationCode(cryptographyUtil.messageAuthenticationCode(sessionKey.getSessionKey(), protectedMessage));
        } catch (Exception e) {
            e.printStackTrace();
            logEvent("{\"event\": \"protect_message_error\", \"data\":\""+thingId+"\"}");
        }

        if(newSessionKey) {
            // Create signature of message (of all fields except signature)
            try {
                protectedMessage.setMessageSignature(cryptographyUtil.ecdsaSignature(thingPrivateKey, protectedMessage));
            } catch (Exception e) {
                e.printStackTrace();
                logEvent("{\"event\": \"protect_message_error\", \"data\":\""+thingId+"\"}");
            }
        }

        logEvent("{\"event\": \"protect_message_success\", \"data\":\""+thingId+"\"}");

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
    public byte[] retrieveMessage(Message protectedMessage, boolean sessionKeyIsNew) {
        logEvent("{\"event\": \"retrieve_message\", \"data\":\""+thingId+"\"}");
        long timestamp = System.currentTimeMillis();

        int decryptionDataSize = 0;
        int numberReceivers = 0;
        int numberMaxReceivers = 0;
        int tenants = 0;

        if(protectedMessage != null && protectedMessage.validate(timestamp)) {
            SessionKey sessionKey = decryptionSessionKeys.get(protectedMessage.getSessionId());

            if ((sessionKey == null || (sessionKey != null && !sessionKey.validate(timestamp)) || sessionKeyIsNew)
                    && protectedMessage.hasOptionals()) {
                // Get session key from optional data

                X509Certificate senderCertificate = null;
                try {

                    CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509", "BC");
                    InputStream in = new ByteArrayInputStream(protectedMessage.getThingCertificate());
                    senderCertificate = (X509Certificate)certificateFactory.generateCertificate(in);
                    // TODO: 06/03/16 Validate / check certificate (Signature, CRL)

                    if(!cryptographyUtil.checkEcdsaSignature(senderCertificate.getPublicKey(), protectedMessage)) {
                        // Signature is invalid
                        log.warn("Signature is invalid");
                        logEvent("{\"event\": \"retrieve_message_error\", \"data\":\""+thingId+"\"}");
                        return null;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                numberReceivers = protectedMessage.getEncryptedSessionKeys().keySet().size();
                byte[] encryptedSessionKey = protectedMessage.getEncryptedSessionKeys().get(CryptographyUtil.secureHash(thingId));

                if(encryptedSessionKey != null && senderCertificate != null) {
                    if(thingPrivateKey != null) {
                        try {
                            decryptionDataSize = thingPrivateKey.getEncoded().length;
                            byte[] ecdhKey = cryptographyUtil.ecdhKeyAgreement(thingPrivateKey,
                                    senderCertificate.getPublicKey());

                            byte[] key = cryptographyUtil.aesDecrypt(ecdhKey, protectedMessage.getIv(), encryptedSessionKey, true);

                            sessionKey = new SessionKey();
                            sessionKey.setSessionId(protectedMessage.getSessionId());
                            sessionKey.setNotValidAfter(protectedMessage.getSessionKeyNotValidAfter());
                            sessionKey.setSessionKey(key);
                            decryptionSessionKeys.put(sessionKey.getSessionId(), sessionKey);

                        } catch (NoSuchProviderException | NoSuchAlgorithmException | InvalidKeyException
                                | IllegalBlockSizeException | NoSuchPaddingException
                                | InvalidAlgorithmParameterException | BadPaddingException e) {
                            log.warn("Error with AES decryption using ECDH key: {}", e.getMessage());
                        }
                    } else {
                        log.warn("Private key of client is null");
                    }
                } else {
                    log.warn("Encrypted session key does not exist for this client");
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
                    logEvent("{\"event\": \"retrieve_message_success\", \"data\":\""+thingId+"\"}");
                    try {
                        String protectedMessageAsJsonString = getObjectMapper().writeValueAsString(protectedMessage);
                        logMeasurement(protectedMessageAsJsonString.length(), null, decryptionDataSize, numberReceivers,
                                numberMaxReceivers, tenants);
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                    return cryptographyUtil.aesDecrypt(sessionKey.getSessionKey(), protectedMessage.getIv(),
                            protectedMessage.getEncryptedMessage(), false);
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
        logEvent("{\"event\": \"retrieve_message_error\", \"data\":\""+thingId+"\"}");

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
    }
}
