package de.ericdoerheit.befiot.simulator;

import de.ericdoerheit.befiot.client.IProtectionClient;
import de.ericdoerheit.befiot.client.Message;
import de.ericdoerheit.befiot.client.ProtectionClient;
import de.ericdoerheit.befiot.core.Util;
import de.ericdoerheit.befiot.registry.Registry;
import de.ericdoerheit.befiot.server.TenantServer;
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

/**
 * This Simulator starts thing clients, tenant servers and the registry with different
 * configurations. As the registry and the tenant servers use spark as HTTP library and
 * this library uses the singleton pattern for the web server, we need to start the
 * servers in different processes to let them listen on different ports.
 */
public class Simulator {
    private final static Logger log = LoggerFactory.getLogger(Simulator.class);

    public static final long ONE_YEAR_MILLISECONDS = 31536000000l; // One year
    public static final int TENANT_BASE_PORT = 8081; // One year
    public static final int TENANT_SERVER_START_DELAY = 2500; // Needs some time to upload the new encryption key agent
    private static final int REGISTRY_START_DELAY = 2500;
    private static final int TEST_DELAY = 2500;

    private Properties clientBaseProperties = new Properties();
    private Properties serverBaseProperties = new Properties();
    private Properties registryBaseProperties = new Properties();

    private String sharedKeyStoreLocation = "keystore.jks";
    private String sharedKeyStorePassword = "befiot";

    private Properties currentRegistryProperties = new Properties();

    private HashMap<String, HashMap<Integer, ProtectionClient>> tenantProtectionClientMap;

    private HashMap<String, Process> tenantProcessMap;
    private HashMap<String, Properties> tenantPropertiesMap;

    private RegistryRunner registryRunner;

    private int testMessageDelay;

    private JedisPool jedisPool;

    public Simulator() {
        tenantProtectionClientMap = new HashMap<>();
        tenantProcessMap = new HashMap<>();
        tenantPropertiesMap = new HashMap<>();
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
            serverBaseProperties.load(new FileInputStream("server.properties"));
            registryBaseProperties.load(new FileInputStream("registry.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        String redisHost = registryBaseProperties.getProperty("redis-host");
        Integer redisPort = Integer.valueOf(registryBaseProperties.getProperty("redis-port"));
        String redisUsername = registryBaseProperties.getProperty("redis-username");
        String redisPassword = registryBaseProperties.getProperty("redis-password");

        jedisPool = new JedisPool(new JedisPoolConfig(), redisHost, redisPort);

        int[] numbersOfTenants;
        int[] numbersOfThingsPerTenant;
        switch (simulation) {
            case SMALL:
                runSmallTest();
                break;
            case STANDARD:
                runStandardTest();
                break;
            case COMPLETE:
                runCompleteTest();
                break;
            case MEASUREMENT:
                numbersOfTenants = new int[]{1, 2, 3, 4, 5};
                numbersOfThingsPerTenant = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
                runMeasurements(numbersOfTenants, numbersOfThingsPerTenant);
                break;
            case R_MEASUREMENT:
                numbersOfTenants = new int[]{1, 2, 3, 4, 5};
                numbersOfThingsPerTenant = new int[]{20};
                runMeasurements(numbersOfTenants, numbersOfThingsPerTenant);
                break;
            case T_MEASUREMENT:
                numbersOfTenants = new int[]{1, 2, 4, 8, 16};
                numbersOfThingsPerTenant = new int[]{1};
                runMeasurements(numbersOfTenants, numbersOfThingsPerTenant);
                break;
            case R_MAX_MEASUREMENT:
                numbersOfTenants = new int[]{1};
                numbersOfThingsPerTenant = new int[]{1, 2, 4, 8, 16};
                runMeasurements(numbersOfTenants, numbersOfThingsPerTenant);
        }
    }

    public void runSmallTest() {
        runTest(1, 1);
    }

    public void runStandardTest() {
        runTest(3, 5);
    }

    public void runCompleteTest() {
        int[] numbersOfTenants = new int[]{1, 2, 5};
        int[] numbersOfThingsPerTenant = new int[]{1, 2, 3, 5, 20};

        for (int i = 0; i < numbersOfTenants.length; i++) {
            for (int j = 0; j < numbersOfThingsPerTenant.length; j++) {
                runTest(numbersOfTenants[i], numbersOfThingsPerTenant[j]);
            }
        }
    }

    public void runMeasurements(int[] numbersOfTenants, int[] numbersOfThingsPerTenant) {
        for (int i = 0; i < numbersOfTenants.length; i++) {
            for (int j = 0; j < numbersOfThingsPerTenant.length; j++) {

                initTest(numbersOfTenants[i], numbersOfThingsPerTenant[j]);
                String tenantId = tenantProtectionClientMap.keySet().iterator().next();
                Integer thingId = 1;

                IProtectionClient senderClient = tenantProtectionClientMap.get(tenantId).get(thingId);
                Set<String> receivers = completeReceiverSet();
                int max = receivers.size();

                for (int k = 0; k < max; k++) {
                    sendSingleMessage(senderClient, receivers);
                    String s = receivers.iterator().next();
                    receivers.remove(s);
                }

                for (Entry<String, Process> entry : tenantProcessMap.entrySet()) {
                    entry.getValue().destroy();
                }

                stop();

                try {
                    Thread.sleep(TEST_DELAY);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void generateThingCertificate(String name) throws IOException, CertificateException, KeyStoreException, NoSuchAlgorithmException, OperatorCreationException, InvalidKeyException, NoSuchProviderException, SignatureException {
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
        keyStore.load(new FileInputStream(sharedKeyStoreLocation), sharedKeyStorePassword.toCharArray());

        // Store private key and certificate
        keyStore.setKeyEntry(name, keyPair.getPrivate(), sharedKeyStorePassword.toCharArray(), new Certificate[]{certificate});

        // Save keystore to file system
        FileOutputStream fis = new FileOutputStream(sharedKeyStoreLocation);
        keyStore.store(fis, sharedKeyStorePassword.toCharArray());

        fis.close();
    }

    private void initTest(int numberOfTenants, int numberOfThingsPerTenant) {

        // Flush Redis DB
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushDB();
        }

        // Start registry
        Properties registryProperties = (Properties) registryBaseProperties.clone();
        initRegistry(registryProperties);

        // Start tenant servers
        for (int i = 0; i < numberOfTenants; i++) {
            Properties serverProperties = (Properties) serverBaseProperties.clone();
            serverProperties.setProperty("tenant-server-port", String.valueOf(TENANT_BASE_PORT+i));
            serverProperties.setProperty("maximum-number-of-things", String.valueOf(numberOfThingsPerTenant));

            String defaultKeyStoreLocation = serverProperties.getProperty("key-store-location");
            String defaultTrustStoreLocation = serverProperties.getProperty("trust-store-location");

            String keyStoreLocation = defaultKeyStoreLocation+"_key-store_" +String.valueOf(TENANT_BASE_PORT+i);
            String trustStoreLocation = defaultTrustStoreLocation+"_trust_store_" +String.valueOf(TENANT_BASE_PORT+i);

            try {
                Files.copy(new File(defaultKeyStoreLocation).toPath(), new File(keyStoreLocation).toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                Files.copy(new File(defaultTrustStoreLocation).toPath(), new File(trustStoreLocation).toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }

            serverProperties.setProperty("key-store-location", keyStoreLocation);
            serverProperties.setProperty("trust-store-location", trustStoreLocation);

            startTenantServer(serverProperties);
        }

        File data = new File("data");
        de.ericdoerheit.befiot.simulator.Util.deleteFolder(data);
        try {
            Files.createDirectory(data.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }


        try {
            Thread.sleep(TEST_DELAY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Start thing clients for each tenant server
        for (int i = 0; i < numberOfTenants; i++) {
            for (int j = 0; j < numberOfThingsPerTenant; j++) {
                Properties clientProperties = (Properties) clientBaseProperties.clone();
                String thingId = String.valueOf(j+1);
                String tenantId = "localhost:"+String.valueOf(TENANT_BASE_PORT+i);
                String fullThingId = thingId+"@"+tenantId;
                clientProperties.setProperty("thing-id", thingId);
                clientProperties.setProperty("data-location", "data"+File.separator+"data_"+fullThingId);
                clientProperties.setProperty("tenant-server-port", String.valueOf(TENANT_BASE_PORT+i));

                try {
                    generateThingCertificate(fullThingId);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (CertificateException e) {
                    e.printStackTrace();
                } catch (KeyStoreException e) {
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                } catch (OperatorCreationException e) {
                    e.printStackTrace();
                } catch (SignatureException e) {
                    e.printStackTrace();
                } catch (NoSuchProviderException e) {
                    e.printStackTrace();
                } catch (InvalidKeyException e) {
                    e.printStackTrace();
                }

                startProtectionClient(clientProperties);
            }
        }


        try {
            Thread.sleep(TEST_DELAY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void runTest(int numberOfTenants, int numberOfThingsPerTenant) {
        log.info("Init test");
        initTest(numberOfTenants, numberOfThingsPerTenant);

        log.info("Send all test messages");
        sendAllTestMessages();
    }

    public void sendAllTestMessages() {
        for (String  tenantId : tenantProtectionClientMap.keySet()) {
            log.info("Send test message from clients of tenant {}", tenantId);
            sendTestMessages(tenantId);
        }
    }

    public void sendTestMessages(String tenantId) {
        for (Integer thingId : tenantProtectionClientMap.get(tenantId).keySet()) {
            log.info("Send test messages from client {}@{}", thingId, tenantId);
            sendTestMessages(tenantId, thingId);
        }
    }

    public void sendTestMessages(String ownTenantId, Integer thingId) {

        IProtectionClient senderClient = tenantProtectionClientMap.get(ownTenantId).get(thingId);

        if (senderClient != null) {
            Set<Set<String>> allReceiverSets = powerSet(completeReceiverSet());

            log.info("{}", allReceiverSets);

            for (Set<String> receivers : allReceiverSets) {
                sendSingleMessage(senderClient, receivers);
            }
        } else {
            log.warn("Sender client is null");
        }

        /*
        // Send messages to all tenants (do test for each tenant separately)
        for (String tenantId : tenantProtectionClientMap.keySet()) {
            HashMap<Integer, ProtectionClient> receiverProtectionClients = tenantProtectionClientMap.get(tenantId);

            int[] totalReceiverSet = new int[receiverProtectionClients.keySet().size()];

            int i = 0;
            for (Entry<Integer, ProtectionClient> entry : receiverProtectionClients.entrySet()) {
                totalReceiverSet[i] = entry.getValue().getThingId();
                i++;
            }

            List<int[]> powerset = Util.powerset(totalReceiverSet);

            for (int[] subset : powerset) {
                senderClient.nextEncryption(tenantId, subset);
                String encryptionHeader = senderClient.encryptionHeader(tenantId);
                byte[] encryptionKey = senderClient.encryptionKeyBytes(tenantId);

                for (int j = 0; j < subset.length; j++) {
                    ProtectionClient receiverClient = tenantProtectionClientMap.get(tenantId).get(subset[i]);

                    if (receiverClient != null) {
                        byte[] decryptionKey = receiverClient.decryptionKeyBytes(tenantId, encryptionHeader, subset);
                        if (!decryptionKey.equals(encryptionKey)) {
                            log.error("Decryption Key does not match to Encryption Key!");
                        } else {
                            log.debug("Decryption Key matches to Encryption Key.");
                        }
                    }
                }
            }
        }
        */
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
                IProtectionClient receiverClient = clientFromClientId(receiverClientId);

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
        Integer thingId = Integer.valueOf(properties.getProperty("thing-id"));
        String tenantId = Util.tenantId(properties.getProperty("tenant-server-host"),
                Integer.valueOf(properties.getProperty("tenant-server-port")));

        HashMap<Integer, ProtectionClient> protectionClientMap = tenantProtectionClientMap.get(tenantId);
        if (protectionClientMap == null) {
            protectionClientMap = new HashMap<Integer, ProtectionClient>();
            tenantProtectionClientMap.put(tenantId, protectionClientMap);
        }

        protectionClientMap.put(thingId, protectionClient);
        protectionClient.start();
    }

    public void startTenantServer(Properties properties) {
        try {
            Process process = executeClassInNewProcess(TenantServer.class, properties);
            String tenantId = Util.tenantId(properties.getProperty("tenant-server-host"),
                    Integer.valueOf(properties.getProperty("tenant-server-port")));
            log.info("Start tenant with id: {}", tenantId);
            tenantProcessMap.put(tenantId, process);
            tenantPropertiesMap.put(tenantId, properties);

            Thread.sleep(TENANT_SERVER_START_DELAY);

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
     * Restart the currently running registry.
     */
    public void restartRegistry() {
        initRegistry(currentRegistryProperties);
    }

    /**
     * Method used to stop the simulator. Stops all clients, tenant servers and the registry.
     */
    public void stop() {
        for (Entry<String, HashMap<Integer, ProtectionClient>> entryMap : tenantProtectionClientMap.entrySet()) {
            for (Entry<Integer, ProtectionClient> entry : entryMap.getValue().entrySet()) {
                entry.getValue().stop();
            }
        }
        tenantProtectionClientMap.clear();

        for (Entry<String, Process> entry : tenantProcessMap.entrySet()) {
            entry.getValue().destroy();
        }
        tenantProcessMap.clear();
        tenantPropertiesMap.clear();

        // stopRegistry();
    }

    /**
     * Helper method to start a class (having a main method) with the given properties in a new process.
     * @param clazz
     * @param properties
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public static Process executeClassInNewProcess(Class clazz, Properties properties) throws IOException, InterruptedException {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome +
                File.separator + "bin" +
                File.separator + "java";
        String classname = clazz.getCanonicalName();
        String classpath = System.getProperty("java.class.path");

        Path temp = Files.createTempFile("tmp", Long.toString(System.nanoTime()));
        FileOutputStream fileOutputStream = new FileOutputStream(temp.toFile());
        properties.store(fileOutputStream, "");

        ProcessBuilder builder = new ProcessBuilder(javaBin, "-cp", classpath, classname, temp.toFile().getAbsolutePath());

        Process process = builder.start();
        fileOutputStream.close();

        return process;
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

    public Set<String> completeReceiverSet() {
        HashSet<String> result = new HashSet<>();

        for (Entry<String, HashMap<Integer, ProtectionClient>> mapEntry : tenantProtectionClientMap.entrySet()) {
            for (Integer thingId : mapEntry.getValue().keySet()) {
                result.add(thingId + "@" + mapEntry.getKey());
            }
        }

        return result;
    }

    public IProtectionClient clientFromClientId(String clientId) {
        String[] parts = clientId.split("@");

        if (parts != null && parts.length > 1) {
            String thingId = parts[0];
            String tenantId = parts[1];

            HashMap<Integer, ProtectionClient> clients = tenantProtectionClientMap.get(tenantId);

            if (clients != null) {
                return clients.get(Integer.valueOf(thingId));
            }
        }

        return null;
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
        simulator.start(Simulation.R_MEASUREMENT, testMessageDelay);
    }
}
