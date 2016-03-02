package de.ericdoerheit.befiot.simulator;

import de.ericdoerheit.befiot.client.ThingClient;
import de.ericdoerheit.befiot.core.Util;
import de.ericdoerheit.befiot.registry.Registry;
import de.ericdoerheit.befiot.server.TenantServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

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

    private Properties clientBaseProperties = new Properties();
    private Properties serverBaseProperties = new Properties();
    private Properties registryBaseProperties = new Properties();

    private Properties currentRegistryProperties = new Properties();

    private HashMap<String, HashMap<Integer, ThingClient>> tenantThingClientMap;
    private HashMap<String, HashMap<Integer, ThingClient>> tenantThingClientThreadsMap;

    private HashMap<String, Process> tenantProcessMap;
    private HashMap<String, Properties> tenantPropertiesMap;

    private Registry registry;
    private Thread registryThread;

    public Simulator() {
        tenantThingClientMap = new HashMap<>();
        tenantProcessMap = new HashMap<>();
        tenantPropertiesMap = new HashMap<>();
    }

    /**
     * The base properties contain the basic configurations needed to run the different
     * component, e.g. database configuration, key store configuration etc. They are
     * loaded from the given properties files.
     */
    public void start() {
        try {
            clientBaseProperties.load(new FileInputStream("client.properties"));
            serverBaseProperties.load(new FileInputStream("server.properties"));
            registryBaseProperties.load(new FileInputStream("registry.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        runAllTests();
    }

    public void runAllTests() {
        int[] numbersOfTenants = new int[]{1, 2, 3};
        int[] numbersOfThingsPerTenant = new int[]{1, 2, 3, 5, 20};

        for (int i = 0; i < numbersOfTenants.length; i++) {
            for (int j = 0; j < numbersOfThingsPerTenant.length; j++) {
                runTest(numbersOfTenants[i], numbersOfThingsPerTenant[j]);
            }
        }
    }

    private void initTest(int numberOfTenants, int numberOfThingsPerTenant) {
        // Start registry
        Properties registryProperties = new Properties(registryBaseProperties);
        startRegistry(registryProperties);

        // Start tenant servers
        for (int i = 0; i < numberOfTenants; i++) {
            Properties serverProperties = new Properties(serverBaseProperties);
            serverProperties.setProperty("tenant-server-port", String.valueOf(8080+i));
            serverProperties.setProperty("maximum-numver-of-things", String.valueOf(numberOfThingsPerTenant));
            startTenantServer(serverProperties);
        }

        // Start thing clients for each tenant server
        for (int i = 0; i < numberOfTenants; i++) {
            for (int j = 0; j < numberOfThingsPerTenant; j++) {
                Properties clientProperties = new Properties(clientBaseProperties);
                clientProperties.setProperty("thing-id", String.valueOf(j+1));
                clientProperties.setProperty("tenant-server-port", String.valueOf(8080+i));
            }
        }
    }

    public void runTest(int numberOfTenants, int numberOfThingsPerTenant) {
        initTest(numberOfTenants, numberOfThingsPerTenant);
        sendAllTestMessages();
    }

    public void sendAllTestMessages() {
        for (String  thingId : tenantThingClientMap.keySet()) {
            sendTestMessage(thingId);
        }
    }

    public void sendTestMessage(String tenantId) {
        for (Integer thingId : tenantThingClientMap.get(tenantId).keySet()) {
            sendTestMessage(tenantId, thingId);
        }
    }

    public void sendTestMessage(String ownTenantId, Integer thingId) {

        ThingClient senderClient = tenantThingClientMap.get(ownTenantId).get(thingId);

        // Send messages to all tenants (do test for each tenant separately)
        for (String tenantId : tenantThingClientMap.keySet()) {
            HashMap<Integer, ThingClient> revceiverThingClients = tenantThingClientMap.get(tenantId);

            int[] totalReceiverSet = new int[revceiverThingClients.keySet().size()];

            int i = 0;
            for (Entry<Integer, ThingClient> entry : revceiverThingClients.entrySet()) {
                totalReceiverSet[i] = entry.getValue().getThingId();
                i++;
            }

            List<int[]> powerset = Util.powerset(totalReceiverSet);

            for (int[] subset : powerset) {
                senderClient.nextEncryption(tenantId, subset);
                String encryptionHeader = senderClient.encryptionHeader(tenantId);
                byte[] encryptionKey = senderClient.encryptionKeyBytes(tenantId);

                for (int j = 0; j < subset.length; j++) {
                    ThingClient receiverClient = tenantThingClientMap.get(tenantId).get(subset[i]);

                    if (receiverClient != null) {
                        byte[] decryptionKey = receiverClient.decryptionKeyBytes(tenantId, encryptionHeader, subset);
                        if (!decryptionKey.equals(encryptionKey)) {
                            log.error("Decryption Key does not match to Encryption Key!");
                        } else {
                            log.debug("Decryption Key matches to Encryption Key.");
                        }
                    }
                }

                // TODO: 12/02/16 For complement of subset -> check that things are not able to decrypt key
            }
        }
    }

    public void startThingClient(Properties properties) {
        // TODO: 14/02/16 MUST BE STARTED IN A NEW THREAD!!!
        ThingClient thingClient = new ThingClient(properties);
        Integer thingId = Integer.valueOf(properties.getProperty("thing-id"));
        String tenantId = properties.getProperty("tenant-id");

        HashMap<Integer, ThingClient> thingClientMap = tenantThingClientMap.get(tenantId);
        if (thingClientMap == null) {
            thingClientMap = new HashMap<Integer, ThingClient>();
            tenantThingClientMap.put(tenantId, thingClientMap);
        }

        thingClientMap.put(thingId, thingClient);
        thingClient.start();
    }

    public void startTenantServer(Properties properties) {
        try {
            Process process = executeClassInNewProcess(TenantServer.class, properties);
            String tenantId = Util.tenantId(properties.getProperty("tenant-server-host"),
                    Integer.valueOf(properties.getProperty("tenant-server-port")));
            tenantProcessMap.put(tenantId, process);
            tenantPropertiesMap.put(tenantId, properties);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void startRegistry(Properties properties) {
        if (registry != null) {
            registry.stop();
        }

        registry = new Registry(properties);
        currentRegistryProperties = properties;
        registry.start();
    }

    public void stopRegistry() {
        if (registry != null) {
            registry.stop();
        }
    }

    public void restartRegistry() {
        stopRegistry();
        startRegistry(currentRegistryProperties);
    }

    /*
    public void shutdown() {
        for (Entry<Integer, ThingClient> entry : tenantThingClientMap.entrySet()) {
            entry.getValue().stop();
        }
        tenantThingClientMap.clear();

        for (Entry<String, Process> entry : tenantProcessMap.entrySet()) {
            entry.getValue().destroy();
        }
        tenantProcessMap.clear();
        tenantPropertiesMap.clear();

        stopRegistry();
    }
    */

    public void stop() {
        // TODO shutdown();
    }

    public static Process executeClassInNewProcess(Class clazz, Properties properties) throws IOException, InterruptedException {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome +
                File.separator + "bin" +
                File.separator + "java";
        String classname = clazz.getCanonicalName();
        String classpath = System.getProperty("java.class.path");

        Path temp = Files.createTempFile("temp", Long.toString(System.nanoTime()));
        FileOutputStream fileOutputStream = new FileOutputStream(temp.toFile());
        properties.store(fileOutputStream, "");

        ProcessBuilder builder = new ProcessBuilder(javaBin, "-cp", classpath, classname, temp.toFile().getName());

        Process process = builder.start();

        return process;
    }

    public static void main(String[] args) {
        Simulator simulator = new Simulator();

        Runtime.getRuntime().addShutdownHook( new Thread() {
            @Override public void run() {
                log.info("Stop simulator.");
                simulator.stop();
            }
        } );

        log.info("Start simulator.");
        simulator.start();
    }
}
