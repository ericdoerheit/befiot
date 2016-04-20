package de.ericdoerheit.efiot.simulator;

import de.ericdoerheit.befiot.registry.Registry;
import de.ericdoerheit.efiot.client.IProtectionClient;
import de.ericdoerheit.efiot.client.Message;
import de.ericdoerheit.efiot.client.ProtectionClient;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.Map.Entry;

import static de.ericdoerheit.befiot.registry.RegistryUtil.REGISTRY_KEY_PREFIX;
import static de.ericdoerheit.befiot.registry.RegistryUtil.TENANT_LIST_KEY;

/**
 * Created by ericdorheit on 08/02/16.
 */

/*
Simulation:
-----------
- Start registry
- Initialize clients
- Create receiver sets
- Encrypt and decrypt random messages
 */

/**
 * This Simulator starts thing clients, tenant servers and the registry with different
 * configurations. As the registry and the tenant servers use spark as HTTP library and
 * this library uses the singleton pattern for the web server, we need to start the
 * servers in different processes to let them listen on different ports.
 */
public class Simulator {
    private final static Logger log = LoggerFactory.getLogger(Simulator.class);

    public static final long ONE_YEAR_MILLISECONDS = 31536000000l; // One year
    private static final int REGISTRY_START_DELAY = 2500;
    private static final int TEST_DELAY = 2500;
    public static final String THING_ID_PREFIX = "thing";

    private Properties clientBaseProperties = new Properties();
    private Properties registryBaseProperties = new Properties();

    private String sharedKeyStoreLocation = "keystore.jks";
    private String sharedKeyStorePassword = "befiot";

    private Properties currentRegistryProperties = new Properties();

    private HashMap<String, ProtectionClient> protectionClients;

    private RegistryRunner registryRunner;

    private int testMessageDelay;

    private JedisPool jedisPool;

    public Simulator() {
        protectionClients = new HashMap<>();
    }

    /**
     * The base properties contain the basic configurations needed to run the different
     * component, e.g. database configuration, key store configuration etc. They are
     * loaded from the given properties files.
     */
    public void start(Simulation simulation, int testMessageDelay) {
        log.info("Start simulator");

        this.testMessageDelay = testMessageDelay;

        // Add bouncy castle as provider
        Security.addProvider(new BouncyCastleProvider());

        try {
            clientBaseProperties.load(new FileInputStream("client.properties"));
            registryBaseProperties.load(new FileInputStream("registry.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        String redisHost = registryBaseProperties.getProperty("redis-host");
        Integer redisPort = Integer.valueOf(registryBaseProperties.getProperty("redis-port"));
        String redisUsername = registryBaseProperties.getProperty("redis-username");
        String redisPassword = registryBaseProperties.getProperty("redis-password");

        jedisPool = new JedisPool(new JedisPoolConfig(), redisHost, redisPort);

        int numbersOfThings = 100;
        switch (simulation) {
            case MEASUREMENT:
                runMeasurements(numbersOfThings);
                break;
        }
    }

    public void runMeasurements(int numberOfThings) {
        initTest(numberOfThings);

        IProtectionClient senderClient = protectionClients.entrySet().iterator().next().getValue();

        if (senderClient != null) {
            Set<String> receivers = protectionClients.keySet();
            int max = receivers.size();

            for (int k = 0; k < max; k++) {
                sendSingleMessage(senderClient, receivers);
                String s = receivers.iterator().next();
                receivers.remove(s);
            }

            stop();
        }

        try {
            Thread.sleep(TEST_DELAY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void createEmptyTrustStore(String trustStoreLocation, String trustStorePassword) throws KeyStoreException,
            CertificateException, NoSuchAlgorithmException, IOException {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, trustStorePassword.toCharArray());

        // Save keystore to file system
        FileOutputStream fis = new FileOutputStream(trustStoreLocation);
        keyStore.store(fis, trustStorePassword.toCharArray());

        fis.close();
    }
    public void generateThingCertificate(String name, String keyStoreLocation, String keyStorePassword) throws IOException, CertificateException, KeyStoreException, NoSuchAlgorithmException, OperatorCreationException, InvalidKeyException, NoSuchProviderException, SignatureException {
        // Generate certificate (self-signed)

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(256);
        KeyPair keyPair = keyGen.generateKeyPair();

        X500Name issuerName = new X500Name("CN="+name+", O=, L=, ST=, C=");
        X500Name subjectName = issuerName; // Issuer is same as subject (self signed)
        BigInteger serial = BigInteger.valueOf(new Random().nextInt()); // Random as serial

        long timestamp = System.currentTimeMillis();
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuerName, serial,
                new Date(timestamp-ONE_YEAR_MILLISECONDS), new Date(timestamp+ONE_YEAR_MILLISECONDS), subjectName, keyPair.getPublic());

        builder.addExtension(Extension.basicConstraints, false, new BasicConstraints(false)); // Certificate does not belong to a CA
        KeyUsage usage = new KeyUsage(KeyUsage.digitalSignature);
        builder.addExtension(Extension.keyUsage, false, usage);

        ASN1EncodableVector purposes = new ASN1EncodableVector();
        purposes.add(KeyPurposeId.id_kp_clientAuth);
        purposes.add(KeyPurposeId.anyExtendedKeyUsage);
        builder.addExtension(Extension.extendedKeyUsage, false, new DERSequence(purposes));

        ContentSigner signer = new JcaContentSignerBuilder("SHA1WITHECDSA").setProvider(BouncyCastleProvider.PROVIDER_NAME).build(keyPair.getPrivate());
        X509Certificate certificate =  new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(builder.build(signer));
        certificate.checkValidity(new Date());
        certificate.verify(keyPair.getPublic());

        // Put certificate in shared trust store of the simulator
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, keyStorePassword.toCharArray());

        // Store private key and certificate
        keyStore.setKeyEntry(name, keyPair.getPrivate(), keyStorePassword.toCharArray(), new Certificate[]{certificate});

        // Save keystore to file system
        FileOutputStream fis = new FileOutputStream(keyStoreLocation);
        keyStore.store(fis, keyStorePassword.toCharArray());

        fis.close();
    }

    private void initTest(int numberOfThings) {

        // Flush Redis DB
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushDB();
        }

        // Start registry
        Properties registryProperties = (Properties) registryBaseProperties.clone();
        initRegistry(registryProperties);

        File data = new File("data");
        de.ericdoerheit.efiot.simulator.Util.deleteFolder(data);
        try {
            Files.createDirectory(data.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Start thing clients for each tenant server
        for (int i = 0; i < numberOfThings; i++) {
            Properties clientProperties = (Properties) clientBaseProperties.clone();
            String thingId = THING_ID_PREFIX+String.valueOf(i+1);
            clientProperties.setProperty("thing-id", thingId);
            clientProperties.setProperty("data-location", "data"+File.separator+"data_"+thingId);

            String defaultKeyStoreLocation = clientProperties.getProperty("key-store-location");
            String keyStorePassword = clientProperties.getProperty("key-store-password");
            String keyStoreLocation = "key_store_" +thingId+"_"+defaultKeyStoreLocation;
            clientProperties.setProperty("key-store-location", keyStoreLocation);

            String defaultTrustStoreLocation = clientProperties.getProperty("receiver-trust-store-location");
            String trustStorePassword = clientProperties.getProperty("receiver-trust-store-password");
            String trustStoreLocation = "trust_store_" +thingId+"_"+defaultTrustStoreLocation;
            clientProperties.setProperty("receiver-trust-store-location", trustStoreLocation);

            try {
                File defaultKeyStoreFile = new File(defaultKeyStoreLocation);
                if(defaultKeyStoreFile.exists()) {
                    Files.copy(defaultKeyStoreFile.toPath(), new File(keyStoreLocation).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }
                generateThingCertificate(thingId, keyStoreLocation, keyStorePassword);

                File defaultTrustStoreFile = new File(defaultTrustStoreLocation);
                if(defaultTrustStoreFile.exists()) {
                    Files.copy(defaultTrustStoreFile.toPath(), new File(trustStoreLocation).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }
                createEmptyTrustStore(trustStoreLocation, trustStorePassword);


            } catch (IOException | InvalidKeyException | NoSuchProviderException | SignatureException
                    | OperatorCreationException | NoSuchAlgorithmException | KeyStoreException
                    | CertificateException e) {
                e.printStackTrace();
            }
            startProtectionClient(clientProperties);
        }


        try {
            Thread.sleep(TEST_DELAY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void sendSingleMessage(IProtectionClient senderClient, Set<String> receivers) {
        if (receivers != null && !receivers.isEmpty()) {
            log.info("{}", receivers);
            byte[] message = new byte[1024];
            new Random().nextBytes(message);
            Message protectedMessage = senderClient.protectMessage(message, System.currentTimeMillis() + ONE_YEAR_MILLISECONDS, receivers, true);
            waitBetweenMessages();
            log.info("{}", protectedMessage);

            for (String receiverClientId : receivers) {
                IProtectionClient receiverClient = protectionClients.get(receiverClientId);

                if (receiverClient != null) {
                    byte[] retrievedMessage = receiverClient.retrieveMessage(protectedMessage, true);
                    boolean successful = Arrays.equals(message, retrievedMessage);
                    log.info("Test message successful: {} Receiver ID: {} Message: {} Retrieved Message: {}", successful, receiverClientId, Arrays.toString(message).hashCode(), Arrays.toString(retrievedMessage).hashCode());
                } else {
                    log.error("Receiver client is not existing!");
                }
            }
            waitBetweenMessages();
        }
    }

    private void waitBetweenMessages() {
        if(testMessageDelay > 0) {
            try {
                Thread.sleep(testMessageDelay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void startProtectionClient(Properties properties) {
        ProtectionClient protectionClient = new ProtectionClient(properties);
        String thingId = properties.getProperty("thing-id");
        protectionClients.put(thingId, protectionClient);
        protectionClient.start();
    }

    /**
     * Initializes and starts the registry.
     * @param properties
     */
    public void initRegistry(Properties properties) {
        if (registryRunner == null) {
            Registry registry = new Registry(properties);
            currentRegistryProperties = properties;
            registryRunner = new RegistryRunner(registry);
            Thread thread = new Thread(registryRunner);
            thread.start();

            try {
                Thread.sleep(REGISTRY_START_DELAY);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Stops the registry if it is running.
     */
    public void stopRegistry() {
        if (registryRunner != null) {
            registryRunner.stop();
        }
    }

    /**
     * Method used to stop the simulator. Stops all clients, tenant servers and the registry.
     */
    public void stop() {
        // TODO Stop registry and delete clients
    }


    private class RegistryRunner implements Runnable {

        private Registry registry;
        private boolean active;

        public RegistryRunner(Registry registry) {
            this.registry = registry;
        }

        public void stop() {
            active = false;
        }

        @Override
        public void run() {
            active = true;
            registry.start();

            while (active) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            registry.stop();
        }
    }

    /*
    Source: http://stackoverflow.com/questions/17891527/optimal-way-to-obtain-get-powerset-of-a-list-recursively
     */
    public static <T> Set<Set<T>> powerSet(Set<T> originalSet) {
        Set<Set<T>> sets = new HashSet<Set<T>>();
        if (originalSet.isEmpty()) {
            sets.add(new HashSet<T>());
            return sets;
        }
        List<T> list = new ArrayList<T>(originalSet);
        T head = list.get(0);
        Set<T> rest = new HashSet<T>(list.subList(1, list.size()));
        for (Set<T> set : powerSet(rest)) {
            Set<T> newSet = new HashSet<T>();
            newSet.add(head);
            newSet.addAll(set);
            sets.add(newSet);
            sets.add(set);
        }
        return sets;
    }
    public static void main(String[] args) {
        Simulator simulator = new Simulator();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                log.info("Stop simulator.");
                simulator.stop();
            }
        });

        log.info("Start simulator.");
        int testMessageDelay = 0;
        simulator.start(Simulation.MEASUREMENT, testMessageDelay);
    }
}
