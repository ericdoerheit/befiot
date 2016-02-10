package de.ericdoerheit.befiot.registry;

import de.ericdoerheit.befiot.core.Deserializer;
import de.ericdoerheit.befiot.core.Serializer;
import de.ericdoerheit.befiot.core.Util;
import de.ericdoerheit.befiot.core.data.EncryptionHeaderData;
import de.ericdoerheit.befiot.core.data.EncryptionKeyAgentData;
import de.ericdoerheit.befiot.core.data.RegistryData;
import de.ericdoerheit.befiot.core.data.TenantData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import spark.Spark;
import sun.security.krb5.internal.crypto.Des;

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

    private Integer maximumTenants;
    private Integer maximumThings;

    private JedisPool jedisPool;

    public Registry(Properties properties) {
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

        maximumTenants = Integer.valueOf(properties.getProperty("maximum-tenants"));
        maximumThings = Integer.valueOf(properties.getProperty("maximum-things"));

        Object[] mandatoryProperties = new Object[]{keyStoreLocation, keyStorePassword, keyPassword,
                trustStoreLocation, trustStorePassword};

        boolean mandatoryPropertiesSet = true;
        for (int i = 0; i < mandatoryProperties.length; i++) {
            mandatoryPropertiesSet = mandatoryPropertiesSet && mandatoryProperties[i] == null;
        }
        if (!mandatoryPropertiesSet) {
            log.error("A mandatory property is not set.");
        }

    }

    public void start() {

        jedisPool = new JedisPool(new JedisPoolConfig(), redisHost, redisPort);

        // TODO: 06/02/16 Filter for authentication of tenants -> Use tenant token

        Spark.port(tenantRegistryPort);
        Spark.secure(keyStoreLocation, keyPassword, trustStoreLocation, trustStorePassword);
        Spark.threadPool(8);

        log.info("Register HTTP endpoints for registry.");

        /**
         * Return information about the registry
         */
        Spark.get("/registry", (req, res) -> {
            log.debug("Request from {} to {}", req.host(), req.port(), req.url());
            RegistryData registryData = new RegistryData();
            registryData.setMaximumTenants(maximumTenants);
            registryData.setMaximumThings(maximumThings);
            return Serializer.registryDataToJsonString(registryData);
        });

        Spark.get("/tenants", (req, res) -> {
            log.debug("Request from {} to {}", req.host(), req.port(), req.url());
            // Load list of tenants from DB
            List<String> tenants;
            try (Jedis jedis = jedisPool.getResource()) {
                tenants = jedis.lrange(REGISTRY_KEY_PREFIX+TENANT_LIST_KEY, 0, -1);
            }
            if (tenants == null) return Collections.emptyList();
            return tenants;
        });

        Spark.get("/tenants/:tenantHostname/:tenantPort", (req, res) -> {
            log.debug("Request from {} to {}", req.host(), req.port(), req.url());
            // Load serialized tenant from DB
            log.debug("Try to load tenant data from DB.");
            String tenant = "";
            try (Jedis jedis = jedisPool.getResource()) {
                tenant = jedis.get(REGISTRY_KEY_PREFIX+TENANT_KEY_PREFIX+tenantKey(req.params(":tenantHostname"), Integer.valueOf(req.params(":tenantPort"))));
            }
            return tenant;
        });

        Spark.post("/tenants", (req, res) -> {
            log.debug("Request from {} to {}", req.host(), req.port(), req.url());
            // Store new Tenant in Redis DB: List of tenant ids and serialized tenant (value) stored under tenant-id (key)
            try (Jedis jedis = jedisPool.getResource()) {
                TenantData inputTenantData = Deserializer.jsonStringToTenantData(req.body());

                // Check is tenant is already registered
                String existingTenantDataJson = jedis.get(REGISTRY_KEY_PREFIX+TENANT_KEY_PREFIX+tenantKey(inputTenantData));

                // TODO: 07/02/16 Validate input
                if (existingTenantDataJson == null && inputTenantData != null && inputTenantData.getHostname() != null && inputTenantData.getPort() != null) {

                    final String tenantKey = tenantKey(inputTenantData);

                    // Every tenant gets the same maximum number of things
                    String offsetString = jedis.get(REGISTRY_KEY_PREFIX+TENANT_THING_ID_RANGE_OFFSET_KEY);
                    Integer offset;
                    if (offsetString == null) {
                        offset = 1;
                    } else {
                        offset = Integer.valueOf(offsetString);
                    }
                    int numberOfThings = maximumThings / maximumTenants;

                    inputTenantData.setToken(randomToken());
                    inputTenantData.setThingIdStart(offset);
                    inputTenantData.setNumberOfThings(maximumThings);
                    inputTenantData.setNumberOfOwnThings(numberOfThings);

                    offset += numberOfThings;

                    // Store new offset
                    jedis.set(REGISTRY_KEY_PREFIX+TENANT_THING_ID_RANGE_OFFSET_KEY, String.valueOf(offset));

                    // Add new tenant id to list
                    jedis.rpush(REGISTRY_KEY_PREFIX+TENANT_LIST_KEY, Util.tenantId(inputTenantData));

                    // Store new tenant
                    String serializedTenantData = Serializer.tenantDataToJsonString(inputTenantData);
                    jedis.set(REGISTRY_KEY_PREFIX+TENANT_KEY_PREFIX+tenantKey, serializedTenantData);

                    res.status(201);
                    return serializedTenantData;
                }
            }
            res.status(500);
            return "";
        });

        Spark.post("/tenants/:tenantHostname/:tenantPort/encryption-key-agent", (req, res) -> {
            log.debug("Request from {} to {}", req.host(), req.port(), req.url());
            try (Jedis jedis = jedisPool.getResource()) {
                String existingTenantDataJson = jedis.get(REGISTRY_KEY_PREFIX+TENANT_KEY_PREFIX + tenantKey(req.params(":tenantHostname"), Integer.valueOf(req.params(":tenantPort"))));

                if (existingTenantDataJson != null) {
                    TenantData existingTenantData = Deserializer.jsonStringToTenantData(existingTenantDataJson);

                    if (existingTenantData != null) {
                        // Append EKA to existing tenant information
                        EncryptionKeyAgentData encryptionKeyAgentData = Serializer.encryptionKeyAgentToData(Deserializer.jsonStringToEncryptionKeyAgent(req.body()));
                        existingTenantData.setEncryptionKeyAgentData(encryptionKeyAgentData);
                        jedis.set(REGISTRY_KEY_PREFIX+TENANT_KEY_PREFIX + tenantKey(req.params(":tenantHostname"), Integer.valueOf(req.params(":tenantPort"))), Serializer.tenantDataToJsonString(existingTenantData));

                    }
                }

                res.status(400);
                return null;
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