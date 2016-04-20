package de.ericdoerheit.befiot.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import spark.Spark;

import static de.ericdoerheit.befiot.registry.RegistryUtil.*;

/**
 * Created by ericdorheit on 06/02/16.
 */
public class Registry {
    private final static Logger log = LoggerFactory.getLogger(Registry.class);

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

    private JedisPool jedisPool;
    private String baseUrl;

    public Registry(Properties properties) {
        tenantRegistryHost = properties.getProperty("tenant-registry-host");
        tenantRegistryPort = Integer.valueOf(properties.getProperty("tenant-registry-port"));

        redisHost = properties.getProperty("redis-host");
        redisPort = Integer.valueOf(properties.getProperty("redis-port"));
        redisUsername = properties.getProperty("redis-username");
        redisPassword = properties.getProperty("redis-password");

        baseUrl = properties.getProperty("base-url", "/encryption-key-agent");

        keyStoreLocation = properties.getProperty("key-store-location");
        keyStorePassword = properties.getProperty("key-store-password");
        keyPassword = properties.getProperty("key-password");

        trustStoreLocation = properties.getProperty("trust-store-location");
        trustStorePassword = properties.getProperty("trust-store-password");

        Object[] mandatoryProperties = new Object[]{keyStoreLocation, keyStorePassword, keyPassword,
                trustStoreLocation, trustStorePassword};

        boolean mandatoryPropertiesSet = true;
        for (int i = 0; i < mandatoryProperties.length; i++) {
            mandatoryPropertiesSet = mandatoryPropertiesSet && mandatoryProperties[i] != null;
        }

        if (!mandatoryPropertiesSet) {
            log.error("A mandatory property is not set.");
        }
    }

    public void start() {

        jedisPool = new JedisPool(new JedisPoolConfig(), redisHost, redisPort);

        Spark.port(tenantRegistryPort);
        Spark.secure(keyStoreLocation, keyPassword, trustStoreLocation, trustStorePassword);
        Spark.threadPool(8);

        log.info("Register HTTP endpoints for registry.");

        Spark.get("/tenants", (req, res) -> {
            log.debug("Request from {} to {}", req.host(), req.port(), req.url());
            // Load list of tenants from DB
            List<String> tenants;
            try (Jedis jedis = jedisPool.getResource()) {
                tenants = jedis.lrange(REGISTRY_KEY_PREFIX + TENANT_LIST_KEY, 0, -1);
            }
            if (tenants == null) return Collections.emptyList();
            return tenants;
        });


        Spark.post(baseUrl + "/:tenantId", (req, res) -> {
            log.debug("Request from {} to {}", req.host(), req.port(), req.url());
            try (Jedis jedis = jedisPool.getResource()) {
                String tenantId = req.params(":tenantId");

                // TODO: 06/02/16 Filter for authentication of tenant / Require signature of EKA and signed certificate

                // TODO: 16/02/16 Use redis transaction
                jedis.set(REGISTRY_KEY_PREFIX + tenantKey(tenantId), req.body());
                jedis.rpush(REGISTRY_KEY_PREFIX + TENANT_LIST_KEY, tenantId);

                res.status(201);
                return "";
            } catch (Exception e) {
                log.error(e.getMessage());
                res.status(500);
                return "";
            }
        });

        Spark.get(baseUrl + "/:tenantId", (req, res) -> {
            log.debug("Request from {} to {}", req.host(), req.port(), req.url());
            try (Jedis jedis = jedisPool.getResource()) {
                String encryptionKeyAgentJson = jedis.get(REGISTRY_KEY_PREFIX + tenantKey(req.params(":tenantId")));
                return encryptionKeyAgentJson;
            }
        });

        Spark.awaitInitialization();
    }

    public void stop() {
        Spark.stop();
        jedisPool.destroy();
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

        Registry registry = new Registry(properties);

        Runtime.getRuntime().addShutdownHook( new Thread() {
            @Override public void run() {
                log.info("Stop registry.");
                registry.stop();
            }
        } );

        registry.start();
    }
}