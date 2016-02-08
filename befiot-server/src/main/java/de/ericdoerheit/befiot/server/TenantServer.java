package de.ericdoerheit.befiot.server;

import de.ericdoerheit.befiot.core.*;
import de.ericdoerheit.befiot.core.data.RegistryData;
import de.ericdoerheit.befiot.core.data.TenantData;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import spark.Spark;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.zip.Inflater;

/**
 * Created by ericdorheit on 05/02/16.
 */
public class TenantServer {
    private final static Logger log = LoggerFactory.getLogger(TenantServer.class);

    public static final String KEY_AGENT_BUILDER_KEY = "key-agent-builder";
    public static final String TENANT_DATA_KEY = "tenant-data";
    public static final String TENANT_DECRYPTION_KEY_AGENT_LIST_KEY = "tenant-decryption-key-agent-list";

    public static final String TENANT_LIST_URL = "/tenants";
    public static final String TENANT_DATA_URL = "/tenant";
    public static final String DECRYPTION_KEY_AGENT_URL = "/decryption-key-agent";

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
    private TenantData tenantData = null;

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

        Object[] mandatoryProperties = new Object[]{tenantServerHost, tenantServerPort, tenantRegistryHost, tenantRegistryPort,
                keyStoreLocation, keyStorePassword, keyPassword, trustStoreLocation, trustStorePassword};

        boolean mandatoryPropertiesSet = true;
        for (int i = 0; i < mandatoryProperties.length; i++) {
            mandatoryPropertiesSet = mandatoryPropertiesSet && mandatoryProperties[i] == null;
        }
        if (!mandatoryPropertiesSet) {
            log.error("A mandatory property is not set.");
        }

        log.debug("New Tenant Server {}", this);
    }

    public void start() {

        SSLContext sslContext = null;
        try {
            sslContext = ServerUtil.getSSLContext(new FileInputStream(keyStoreLocation),
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

        jedisPool = new JedisPool(new JedisPoolConfig(), redisHost, redisPort);

        String keyAgentBuilderJsonString;
        String tenantDataJson;

        try (Jedis jedis = jedisPool.getResource()) {
            keyAgentBuilderJsonString = jedis.get(KEY_AGENT_BUILDER_KEY);
            tenantDataJson = jedis.get(TENANT_DATA_KEY);
        }

        if (keyAgentBuilderJsonString == null || tenantDataJson == null) {
            // Request system data from registry (maximum Decryption Key Agents and id range for this tenant)
            String tenantDataUrl = "https://"+tenantRegistryHost+":"+tenantRegistryPort+TENANT_DATA_URL+"/"
                    +Util.tenantId(tenantServerHost, tenantServerPort);

            Request tenantDataRequest = new Request.Builder()
                    .url(tenantDataUrl)
                    .build();
            try {
                Response response = httpClient.newCall(tenantDataRequest).execute();
                if (200 <= response.code() && response.code() <= 299) {
                    log.debug("Response from request of GET {} Status code: {}", tenantDataUrl, response.code());
                    tenantData = Deserializer.jsonStringToTenantData(response.body().string());
                    try (Jedis jedis = jedisPool.getResource()){
                        jedis.set(TENANT_DATA_KEY, response.body().string());
                    }
                } else {
                    log.error("Could not receive tenant list from {}. Status code: {}", tenantDataUrl, response.code());
                }
            } catch (IOException e) {
                log.error("Could not receive tenant list from {}. {}", tenantDataUrl, e.getMessage());
            }

            if (tenantData != null) {
                // Create new random Key Agent Builder
                keyAgentBuilder = new KeyAgentBuilder(Util.getDefaultPairing(), tenantData.getNumberOfThings());

                // Serialize and store new Key Agent Builder
                keyAgentBuilderJsonString = Serializer.keyAgentBuilderToJsonString(keyAgentBuilder);
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.set(KEY_AGENT_BUILDER_KEY, keyAgentBuilderJsonString);
                }
            } else {
                log.error("Could not create Key Agent Builder. Information about registry is null.");
            }

        } else {
            keyAgentBuilder = Deserializer.jsonStringToKeyAgentBuilder(keyAgentBuilderJsonString);
            tenantData = Deserializer.jsonStringToTenantData(tenantDataJson);
        }

        String[] tenantIds = null;
        String tenantListUrl = "https://"+tenantRegistryHost+":"+tenantRegistryPort+TENANT_LIST_URL;
        Request tenantListRequest = new Request.Builder()
                .url(tenantListUrl)
                .build();
        try {
            Response response = httpClient.newCall(tenantListRequest).execute();
            if (200 <= response.code() && response.code() <= 299) {
                log.debug("Response from request of GET {} Status code: {}", tenantListUrl, response.code());
                // TODO: 08/02/16 Move body parsing to Core Util class (Deserializer) and use json
                tenantIds = response.body().string().split(",");
            } else {
                log.error("Could not receive tenant list from {}. Status code: {}", tenantListUrl, response.code());
            }
        } catch (IOException e) {
            log.error("Could not receive tenant list from {}. {}", tenantListUrl, e.getMessage());
        }

        if (tenantIds != null) {
            for (int i = 0; i < tenantIds.length; i++) {
                String tenant = tenantIds[i];

                List<String> decryptionKeyAgentIds =null;
                try (Jedis jedis = jedisPool.getResource()) {
                    decryptionKeyAgentIds = jedis.lrange(TENANT_DECRYPTION_KEY_AGENT_LIST_KEY+":"+tenant, 0, -1);
                }

                if(decryptionKeyAgentIds == null) {
                    decryptionKeyAgentIds = Collections.emptyList();
                }


                // Not all DKAs are stored -> Get DKAs from tenant
                if(decryptionKeyAgentIds.size() < tenantData.getNumberOfOwnThings()) {
                    List<Integer> missingDecryptionKeyAgentIds = new ArrayList<Integer>();

                    for (int j = 1; j <= tenantData.getNumberOfOwnThings(); j++) {
                        missingDecryptionKeyAgentIds.add(j);
                    }

                    for (String s : decryptionKeyAgentIds) {
                        missingDecryptionKeyAgentIds.remove(Integer.valueOf(s)-1);
                    }

                    for (Integer decryptionKeyAgentId : missingDecryptionKeyAgentIds) {
                        String decryptionKeyAgentUrl = "https://" + tenant + DECRYPTION_KEY_AGENT_URL + "/" + decryptionKeyAgentId;
                        Request decryptionKeyAgentRequest = new Request.Builder()
                                .url(decryptionKeyAgentUrl)
                                .build();
                        try {
                            Response response = httpClient.newCall(decryptionKeyAgentRequest).execute();
                            if (200 <= response.code() && response.code() <= 299) {
                                log.debug("Response from request of GET {} Status code: {}", decryptionKeyAgentUrl, response.code());
                                missingDecryptionKeyAgentIds.remove(decryptionKeyAgentId-1);
                                try (Jedis jedis = jedisPool.getResource()) {
                                    String tenantId = tenant;
                                    String thingId = String.valueOf(decryptionKeyAgentId);
                                    jedis.set("decryption-key-agent:" + tenantId + ":" + thingId, response.body().string());
                                    jedis.rpush(TENANT_DECRYPTION_KEY_AGENT_LIST_KEY, thingId);
                                }
                            } else {
                                log.error("Could not receive decryption key agent from {}. Status code: {}", decryptionKeyAgentUrl, response.code());
                            }
                        } catch (IOException e) {
                            log.error("Could not receive decryption key agent from {}. {}", decryptionKeyAgentUrl, e.getMessage());
                        }
                    }
                }
            }
        }

        /* --- Register HTTP Handler --- */
        Spark.port(tenantServerPort);
        Spark.secure(keyStoreLocation, keyPassword, trustStoreLocation, trustStorePassword);
        Spark.threadPool(8);

        Spark.get("/tenants", (req, res) -> {
            res.header("Content-Type", "application/json");
            String encryptionKeyAgent = Serializer.encryptionKeyAgentToJsonString(keyAgentBuilder.getEncryptionKeyAgent());
            return encryptionKeyAgent;
        });

        Spark.get("/encryption-key-agent", (req, res) -> {
            res.header("Content-Type", "application/json");
            String encryptionKeyAgent = Serializer.encryptionKeyAgentToJsonString(keyAgentBuilder.getEncryptionKeyAgent());
            return encryptionKeyAgent;
        });

        Spark.get("/decryption-key-agent/:tenantId/:id", (req, res) -> {
            res.header("Content-Type", "application/json");
            String thingId = req.params(":id");
            String tenantId = req.params(":tenantId");

            if(Util.tenantId(tenantServerHost, tenantServerPort).equals(tenantId)) {
                String decryptionKeyAgent = Serializer.decryptionKeyAgentToJsonString(keyAgentBuilder.getDecryptionKeyAgent(Integer.valueOf(thingId)));
                return decryptionKeyAgent;
            } else {
                try (Jedis jedis = jedisPool.getResource()) {
                    return jedis.get("decryption-key-agent:" + tenantId + ":" + thingId);
                }
            }
        });

        Spark.get("/decryption-key-agent/:id", (req, res) -> {
            res.header("Content-Type", "application/json");
            String thingId = req.params(":id");

            // TODO: 08/02/16 Check if id is in the range of the requesting tenant (-> whether requesting tenant is authorized)
            
            String decryptionKeyAgent = Serializer.decryptionKeyAgentToJsonString(keyAgentBuilder.getDecryptionKeyAgent(Integer.valueOf(thingId)));
            return decryptionKeyAgent;
        });
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

        TenantServer tenantServer = new TenantServer(properties);
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
}
