package de.ericdoerheit.befiot.simulator;

import de.ericdoerheit.befiot.client.ThingClient;
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
import java.util.Properties;

/**
 * Created by ericdorheit on 08/02/16.
 */
public class Simulator {
    private final static Logger log = LoggerFactory.getLogger(Simulator.class);

    public static void main(String[] args) {

        Properties clientBaseProperties = new Properties();
        Properties serverBaseProperties = new Properties();
        Properties registryBaseProperties = new Properties();
        try {
            clientBaseProperties.load(new FileInputStream("client.properties"));
            serverBaseProperties.load(new FileInputStream("server.properties"));
            registryBaseProperties.load(new FileInputStream("registry.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Properties clientProperties = new Properties(clientBaseProperties);
        clientProperties.setProperty("thing-id", "1");

        ThingClient thingClient = new ThingClient(clientProperties);

        Properties serverProperties = new Properties(serverBaseProperties);
        TenantServer tenantServer = new TenantServer(serverProperties);

        Properties registryProperties = new Properties(registryBaseProperties);
        registryProperties.setProperty("maximum-tenants", "1");
        registryProperties.setProperty("maximum-things", "2");
        Registry registry = new Registry(registryProperties);

        /* --- Configuration 01: 1 Registry, 1 Server, 1 Client --- */
        log.info("Start registry.");
        registry.start();

        // TODO: 09/02/16 Start processes with new servers... (Spark uses a Singleton..)
        log.info("Start tenant server.");


        try {
            executeClassInNewProcess(TenantServer.class, serverProperties);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            //tenantServer.start();
        } catch (Exception e) {
            log.error(e.getMessage());
        }

        log.info("Start thing client.");
        //thingClient.start();


        Runtime.getRuntime().addShutdownHook( new Thread() {
            @Override public void run() {
                log.info("Stop thing client.");
                thingClient.stop();

                log.info("Stop tenant server.");
                tenantServer.stop();

                log.info("Stop registry.");
                registry.stop();
            }
        } );
    }

    public static int executeClassInNewProcess(Class clazz, Properties properties) throws IOException, InterruptedException {
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
        process.waitFor();

        return process.exitValue();
    }
}
