package de.ericdoerheit.befiot.server;

import de.ericdoerheit.befiot.core.Deserializer;
import de.ericdoerheit.befiot.core.KeyAgentBuilder;
import de.ericdoerheit.befiot.core.Serializer;
import de.ericdoerheit.befiot.core.Util;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import spark.Spark;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStoreException;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import static de.ericdoerheit.befiot.server.ServerUtil.TENANT_SERVER_KEY_PREFIX;
import static de.ericdoerheit.befiot.server.ServerUtil.logEvent;

/**
 * Created by ericdorheit on 05/02/16.
 */
public class TenantServer {
    private final static Logger log = LoggerFactory.getLogger(TenantServer.class);

    public static final String KEY_AGENT_BUILDER_KEY = "key-agent-builder";

    public static final String DECRYPTION_KEY_AGENT_URL = "/decryption-key-agent";
    public static final String REGISTRY_ENCRYPTION_KEY_AGENT_URL = "/encryption-key-agent";

    private String tenantToken;

    private String tenantServerHost;
    private Integer tenantServerPort;

    private String tenantRegistryHost;
    private Integer tenantRegistryPort;

    private String redisHost;
    private String redisUsername;
    private String redisPassword;
    private Integer redisPort;

    private String keyStoreLocation;
    private String keyStorePassword;
    private String keyPassword;

    private String trustStoreLocation;
    private String trustStorePassword;

    private OkHttpClient httpClient;
    private JedisPool jedisPool;

    private KeyAgentBuilder keyAgentBuilder;
    private Integer maximumNumberOfThings;
    private Long keyAgentBuilderLifetime;

    private TimerTask uploadTask;
    Timer timer;

    public TenantServer(Properties properties) {
        tenantToken = properties.getProperty("tenant-token");

        tenantServerHost = properties.getProperty("tenant-server-host");
        tenantServerPort = Integer.valueOf(properties.getProperty("tenant-server-port"));

        tenantRegistryHost = properties.getProperty("tenant-registry-host");
        tenantRegistryPort = Integer.valueOf(properties.getProperty("tenant-registry-port"));

        redisHost = properties.getProperty("redis-host");
        redisPort = Integer.valueOf(properties.getProperty("redis-port"));
        redisUsername = properties.getProperty("redis-username");
        redisPassword = properties.getProperty("redis-password");

        keyStoreLocation = properties.getProperty("key-store-location");
        keyStorePassword = properties.getProperty("key-store-password");
        keyPassword = properties.getProperty("key-password");

        trustStoreLocation = properties.getProperty("trust-store-location");
        trustStorePassword = properties.getProperty("trust-store-password");

        maximumNumberOfThings = Integer.valueOf(properties.getProperty("maximum-number-of-things"));
        keyAgentBuilderLifetime = Long.valueOf(properties.getProperty("key-agent-builder-lifetime"));

        Object[] mandatoryProperties = new Object[]{tenantServerHost, tenantServerPort, tenantRegistryHost, tenantRegistryPort,
                keyStoreLocation, keyStorePassword, keyPassword, trustStoreLocation, trustStorePassword, maximumNumberOfThings,
                keyAgentBuilderLifetime};

        boolean mandatoryPropertiesSet = true;
        for (int i = 0; i < mandatoryProperties.length; i++) {
            mandatoryPropertiesSet = mandatoryPropertiesSet && mandatoryProperties[i] != null;
        }

        if (!mandatoryPropertiesSet) {
            log.error("A mandatory property is not set.");
        }

        log.debug("New Tenant Server {}", this);

        timer = new Timer();
    }

    public void start() {
        logEvent("{\"event\": \"server_started\", \"data\":\""+Util.tenantId(tenantServerHost, tenantServerPort)+"\"}");

        log.info("Start tenant");

        SSLContext sslContext = null;
        try {
            FileInputStream fisKeyStore = new FileInputStream(keyStoreLocation);
            FileInputStream fisTrustStore = new FileInputStream(trustStoreLocation);
            sslContext = ServerUtil.getSSLContext(fisKeyStore,
                    fisTrustStore, keyStorePassword, keyPassword, trustStorePassword);

            fisKeyStore.close();
            fisTrustStore.close();

        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (Exception e) {
            log.error("SSLContext initialization error: {}", e.getMessage());
        }
        httpClient = new OkHttpClient.Builder()
                .sslSocketFactory(sslContext.getSocketFactory())
                .build();

        jedisPool = new JedisPool(new JedisPoolConfig(), redisHost, redisPort);

        // Init key agent builder
        if(initializeKeyAgentBuilder()) {
            // Try to upload EKA until successful
            uploadTask = new TimerTask() {
                @Override
                public void run() {
                    uploadEncryptionKeyAgentToRegistry();
                    this.cancel();
                    timer.cancel();
                }
            };
            timer.schedule(uploadTask, 100, 5000);

            /* --- Register HTTP Handler --- */
            log.info("Register HTTP endpoints for tenant server.");

            Spark.port(tenantServerPort);
            Spark.secure(keyStoreLocation, keyPassword, trustStoreLocation, trustStorePassword);
            Spark.threadPool(24);

            Spark.get("/decryption-key-agent/:id", (req, res) -> {
                log.debug("Request from {} to {}", req.host(), req.port(), req.url());
                res.header("Content-Type", "application/json");
                String thingId = req.params(":id");

                String decryptionKeyAgentString = Serializer.decryptionKeyAgentToJsonString(keyAgentBuilder.getDecryptionKeyAgent(Integer.valueOf(thingId)));
                return decryptionKeyAgentString;
            });

            Spark.get("/status", (req, res) -> {
                return "running";
            });

            Spark.awaitInitialization();

        } else {
            log.error("Key agent builder could not be initialized.");
        }
    }

    public boolean initializeKeyAgentBuilder() {
        String keyAgentBuilderJsonString;

        try (Jedis jedis = jedisPool.getResource()) {
            keyAgentBuilderJsonString = jedis.get(dbPrefix()+KEY_AGENT_BUILDER_KEY);
        }

        if (keyAgentBuilderJsonString != null) {
            keyAgentBuilder = Deserializer.jsonStringToKeyAgentBuilder(keyAgentBuilderJsonString);

            // TODO: 16/03/16 Check if KAB fits to server properties (maximum number of things etc.)
            
            return true;
        } else {
            // Create new random Key Agent Builder
            long timestamp = System.currentTimeMillis();
            keyAgentBuilder = new KeyAgentBuilder(timestamp, timestamp+keyAgentBuilderLifetime,
                    Util.getDefaultPairing(), maximumNumberOfThings);

            // Serialize and store new Key Agent Builder
            keyAgentBuilderJsonString = Serializer.keyAgentBuilderToJsonString(keyAgentBuilder);
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.set(dbPrefix()+KEY_AGENT_BUILDER_KEY, keyAgentBuilderJsonString);
            } catch (Exception e) {
                log.error(e.getMessage());
                return false;
            }
            return true;
        }
    }

    public boolean uploadEncryptionKeyAgentToRegistry() {
        String registryEkaUrl = "https://" + tenantRegistryHost + ":" + tenantRegistryPort + REGISTRY_ENCRYPTION_KEY_AGENT_URL
                + "/" + Util.tenantId(tenantServerHost, tenantServerPort);

        String encryptionKeyAgentString = Serializer.encryptionKeyAgentToJsonString(keyAgentBuilder.getEncryptionKeyAgent());
        RequestBody registryEkaRequestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"),
                encryptionKeyAgentString);

        Request registryEkaRequest = new Request.Builder()
                .url(registryEkaUrl)
                .post(registryEkaRequestBody)
                .build();

        try {
            log.debug("Request to {}.", registryEkaUrl);
            Response response = httpClient.newCall(registryEkaRequest).execute();
            String body = response.body().string();

            if (200 <= response.code() && response.code() <= 299) {
                log.debug("Response from request of POST {} Status code: {}", registryEkaUrl, response.code());
                log.info("Encryption key agent successfully uploaded to registry.");
                return true;

            } else {
                log.error("Could not upload encryption key agent to registry ({}). Status code: {}", registryEkaUrl, response.code());
            }

        } catch (IOException e) {
            log.error("Could not receive tenant data from {}. {}", registryEkaUrl, e.getMessage());
        }
        return false;
    }

    public void stop() {
        logEvent("{\"event\": \"server_stopped\", \"data\":\""+Util.tenantId(tenantServerHost, tenantServerPort)+"\"}");
        Spark.stop();
        jedisPool.destroy();
        if(uploadTask != null) {
            uploadTask.cancel();
        }

        if (timer != null) {
            timer.cancel();
        }
    }

    public static void main(String[] args) {
        Properties properties = new Properties();

        if(args.length == 0) {
            log.error("No configuration file is give.");
            return;
        }

        String propertiesLocation = args[0];

        try {
            properties.load(new FileInputStream(propertiesLocation));
        } catch (IOException e) {
            log.error("Configuration file not found.");
            return;
        }

        TenantServer tenantServer = new TenantServer(properties);

        Runtime.getRuntime().addShutdownHook( new Thread() {
            @Override public void run() {
                log.info("Stop tenant server.");
                tenantServer.stop();
            }
        } );

        tenantServer.start();
    }

    @Override
    public String toString() {
        return "TenantServer{" +
                "tenantToken='" + tenantToken + '\'' +
                ", tenantServerHost='" + tenantServerHost + '\'' +
                ", tenantServerPort=" + tenantServerPort +
                ", tenantRegistryHost='" + tenantRegistryHost + '\'' +
                ", tenantRegistryPort=" + tenantRegistryPort +
                ", redisHost='" + redisHost + '\'' +
                ", redisUsername='" + redisUsername + '\'' +
                ", redisPassword='" + redisPassword + '\'' +
                ", redisPort=" + redisPort +
                ", keyStoreLocation='" + keyStoreLocation + '\'' +
                ", keyStorePassword='" + keyStorePassword + '\'' +
                ", keyPassword='" + keyPassword + '\'' +
                ", trustStoreLocation='" + trustStoreLocation + '\'' +
                ", trustStorePassword='" + trustStorePassword + '\'' +
                ", keyAgentBuilder=" + keyAgentBuilder +
                '}';
    }

    private String dbPrefix() {
        return TENANT_SERVER_KEY_PREFIX+Util.tenantId(tenantServerHost, tenantServerPort)+":";
    }
}
