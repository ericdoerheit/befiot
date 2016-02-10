package de.ericdoerheit.befiot.server;

import de.ericdoerheit.befiot.core.*;
import de.ericdoerheit.befiot.core.data.RegistryData;
import de.ericdoerheit.befiot.core.data.TenantData;
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
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.zip.Inflater;

import static de.ericdoerheit.befiot.server.ServerUtil.*;

/**
 * Created by ericdorheit on 05/02/16.
 */
public class TenantServer {
    private final static Logger log = LoggerFactory.getLogger(TenantServer.class);

    public static final String KEY_AGENT_BUILDER_KEY = "key-agent-builder";
    public static final String TENANT_DATA_KEY = "tenant-data";
    public static final String TENANT_LIST_KEY = "tenant-list";
    public static final String TENANT_DECRYPTION_KEY_AGENT_LIST_KEY = "tenant-decryption-key-agent-list";

    public static final String TENANT_LIST_URL = "/tenants";
    public static final String TENANT_DATA_URL = "/tenants";
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
    private TenantData tenantData;

    private TimerTask updateTask;

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
        } catch (Exception e) {
            log.error("SSLContext initialization error: {}", e.getMessage());
        }
        httpClient = new OkHttpClient.Builder()
                .sslSocketFactory(sslContext.getSocketFactory())
                .build();

        jedisPool = new JedisPool(new JedisPoolConfig(), redisHost, redisPort);

        String keyAgentBuilderJsonString;
        String tenantDataJson;

        try (Jedis jedis = jedisPool.getResource()) {
            keyAgentBuilderJsonString = jedis.get(dbPrefix()+KEY_AGENT_BUILDER_KEY);
            tenantDataJson = jedis.get(dbPrefix()+TENANT_DATA_KEY);
        }

        if (tenantDataJson != null) {
            tenantData = Deserializer.jsonStringToTenantData(tenantDataJson);
        } else {
            boolean tenantNotRegistered = false;
            // Request system data from registry (maximum Decryption Key Agents and id range for this tenant)
            String tenantDataUrl = "https://"+tenantRegistryHost+":"+tenantRegistryPort+TENANT_DATA_URL+"/" + tenantServerHost + "/" + String.valueOf(tenantServerPort);
            Request tenantDataRequest = new Request.Builder()
                    .url(tenantDataUrl)
                    .build();
            try {
                log.debug("Request to {}.", tenantDataUrl);
                Response response = httpClient.newCall(tenantDataRequest).execute();
                if (200 <= response.code() && response.code() <= 299) {
                    log.debug("Response from request of GET {} Status code: {}", tenantDataUrl, response.code());
                    String body = response.body().string();
                    tenantData = Deserializer.jsonStringToTenantData(body);
                    try (Jedis jedis = jedisPool.getResource()) {
                        jedis.set(dbPrefix() + TENANT_DATA_KEY, body);
                    }
                } else if (response.code() == 404) {
                    tenantNotRegistered = true;
                } else {
                    log.error("Could not receive tenant data from {}. Status code: {}", tenantDataUrl, response.code());
                }
                response.body().close();
            } catch (IOException e) {
                log.error("Could not receive tenant data from {}. {}", tenantDataUrl, e.getMessage());
            }

            if(tenantNotRegistered) {
                // Request system data from registry (maximum Decryption Key Agents and id range for this tenant)
                String registerTenantUrl = "https://"+tenantRegistryHost+":"+tenantRegistryPort+TENANT_DATA_URL;
                tenantData = new TenantData();
                tenantData.setHostname(tenantServerHost);
                tenantData.setPort(tenantServerPort);
                RequestBody registerTenantBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), Serializer.tenantDataToJsonString(tenantData));
                Request registerTenantRequest = new Request.Builder()
                        .url(registerTenantUrl)
                        .post(registerTenantBody)
                        .build();
                try {
                    log.debug("Request to {}.", registerTenantUrl);
                    Response response = httpClient.newCall(registerTenantRequest).execute();
                    if (200 <= response.code() && response.code() <= 299) {
                        log.debug("Response from request of POST {} Status code: {}", registerTenantUrl, response.code());
                        String body = response.body().string();
                        tenantData = Deserializer.jsonStringToTenantData(body);
                        try (Jedis jedis = jedisPool.getResource()) {
                            jedis.set(dbPrefix() + TENANT_DATA_KEY, body);
                        }
                    } else {
                        log.error("Could not register tenant at {}. Status code: {}", registerTenantUrl, response.code());
                    };
                    response.body().close();
                } catch (IOException e) {
                    log.error("Could not register tenant at {}. {}", registerTenantUrl, e.getMessage());
                }
            }
        }

        if (keyAgentBuilderJsonString != null) {
            keyAgentBuilder = Deserializer.jsonStringToKeyAgentBuilder(keyAgentBuilderJsonString);
        } else {
            if (tenantData != null) {
                // Create new random Key Agent Builder
                keyAgentBuilder = new KeyAgentBuilder(Util.getDefaultPairing(), tenantData.getNumberOfThings());

                // Serialize and store new Key Agent Builder
                keyAgentBuilderJsonString = Serializer.keyAgentBuilderToJsonString(keyAgentBuilder);
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.set(dbPrefix()+KEY_AGENT_BUILDER_KEY, keyAgentBuilderJsonString);
                }
            } else {
                log.error("Could not create Key Agent Builder. Tenant data is null.");
            }
        }
        
        if(tenantData != null && tenantData.getEncryptionKeyAgentData() == null) {
            // TODO: 09/02/16 Post EKA to registry
        }

        update();
        Timer timer = new Timer();
        updateTask = new TimerTask() {
            @Override
            public void run() {
                update();
            }
        };
        timer.schedule(updateTask, 2500, 2500);

        /* --- Register HTTP Handler --- */
        log.info("Register HTTP endpoints for tenant server.");

        Spark.port(tenantServerPort);
        Spark.secure(keyStoreLocation, keyPassword, trustStoreLocation, trustStorePassword);
        Spark.threadPool(8);

        Spark.get("/tenants", (req, res) -> {
            res.header("Content-Type", "application/json");
            log.debug("Request from {} to {}", req.host(), req.port(), req.url());
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.get(dbPrefix()+TENANT_LIST_KEY);
            } finally {
                return Collections.emptyList();
            }
        });

        Spark.get("/encryption-key-agent", (req, res) -> {
            log.debug("Request from {} to {}", req.host(), req.port(), req.url());
            res.header("Content-Type", "application/json");
            String encryptionKeyAgent = Serializer.encryptionKeyAgentToJsonString(keyAgentBuilder.getEncryptionKeyAgent());
            return encryptionKeyAgent;
        });

        Spark.get("/decryption-key-agent/:tenantId/:id", (req, res) -> {
            log.debug("Request from {} to {}", req.host(), req.port(), req.url());
            res.header("Content-Type", "application/json");
            String thingId = req.params(":id");
            String tenantId = req.params(":tenantId");

            if(Util.tenantId(tenantServerHost, tenantServerPort).equals(tenantId)) {
                String decryptionKeyAgent = Serializer.decryptionKeyAgentToJsonString(keyAgentBuilder.getDecryptionKeyAgent(Integer.valueOf(thingId)));
                return decryptionKeyAgent;
            } else {
                try (Jedis jedis = jedisPool.getResource()) {
                    return jedis.get(dbPrefix()+"decryption-key-agent:" + tenantId + ":" + thingId);
                }
            }
        });

        Spark.get("/decryption-key-agent/:id", (req, res) -> {
            log.debug("Request from {} to {}", req.host(), req.port(), req.url());
            res.header("Content-Type", "application/json");
            String thingId = req.params(":id");

            // TODO: 08/02/16 Check if id is in the range of the requesting tenant (-> whether requesting tenant is authorized)
            
            String decryptionKeyAgent = Serializer.decryptionKeyAgentToJsonString(keyAgentBuilder.getDecryptionKeyAgent(Integer.valueOf(thingId)));
            return decryptionKeyAgent;
        });

        Spark.awaitInitialization();
    }

    private void update() {
        String[] tenantIds = null;
        String tenantListUrl = "https://"+tenantRegistryHost+":"+tenantRegistryPort+TENANT_LIST_URL;
        Request tenantListRequest = new Request.Builder()
                .url(tenantListUrl)
                .build();
        try {
            log.debug("Request to {}.", tenantListUrl);
            Response response = httpClient.newCall(tenantListRequest).execute();
            if (200 <= response.code() && response.code() <= 299) {
                log.debug("Response from request of GET {} Status code: {}", tenantListUrl, response.code());
                // TODO: 08/02/16 Move body parsing to Core Util class (Deserializer) and use json
                tenantIds = response.body().string().replaceAll("\\s|\\[|\\]","").split(",");
            } else {
                log.error("Could not receive tenant list from {}. Status code: {}", tenantListUrl, response.code());
            }
            response.body().close();
        } catch (IOException e) {
            log.error("Could not receive tenant list from {}. {}", tenantListUrl, e.getMessage());
        }

        if (tenantIds != null) {
            List<String> tenantList = null;
            try (Jedis jedis = jedisPool.getResource()) {
                tenantList = jedis.lrange(dbPrefix() + TENANT_LIST_KEY, 0, -1);
            }
            for (int i = 0; i < tenantIds.length; i++) {
                String tenant = tenantIds[i];

                if (!tenant.equals(Util.tenantId(tenantServerHost, tenantServerPort))) {

                    if (tenantList == null || !tenantList.contains(tenant)) {
                        try (Jedis jedis = jedisPool.getResource()) {
                            jedis.rpush(dbPrefix() + TENANT_LIST_KEY, tenant);
                        }
                    }

                    List<String> decryptionKeyAgentIds = null;
                    try (Jedis jedis = jedisPool.getResource()) {
                        decryptionKeyAgentIds = jedis.lrange(dbPrefix() + TENANT_DECRYPTION_KEY_AGENT_LIST_KEY + ":" + tenant, 0, -1);
                    }

                    if (decryptionKeyAgentIds == null) {
                        decryptionKeyAgentIds = Collections.emptyList();
                    }

                    // Not all DKAs are stored -> Get DKAs from tenant
                    if (decryptionKeyAgentIds.size() < tenantData.getNumberOfOwnThings()) {
                        List<Integer> missingDecryptionKeyAgentIds = new ArrayList<Integer>();

                        for (int j = 1; j <= tenantData.getNumberOfOwnThings(); j++) {
                            missingDecryptionKeyAgentIds.add(j);
                        }

                        for (String s : decryptionKeyAgentIds) {
                            missingDecryptionKeyAgentIds.remove(Integer.valueOf(s) - 1);
                        }

                        for (Integer decryptionKeyAgentId : missingDecryptionKeyAgentIds) {
                            String decryptionKeyAgentUrl = "https://" + tenant + DECRYPTION_KEY_AGENT_URL + "/" + decryptionKeyAgentId;
                            Request decryptionKeyAgentRequest = new Request.Builder()
                                    .url(decryptionKeyAgentUrl)
                                    .build();
                            try {
                                log.debug("Request to {}.", decryptionKeyAgentUrl);
                                Response response = httpClient.newCall(decryptionKeyAgentRequest).execute();
                                if (200 <= response.code() && response.code() <= 299) {
                                    log.debug("Response from request of GET {} Status code: {}", decryptionKeyAgentUrl, response.code());
                                    missingDecryptionKeyAgentIds.remove(decryptionKeyAgentId - 1);
                                    try (Jedis jedis = jedisPool.getResource()) {
                                        String tenantId = tenant;
                                        String thingId = String.valueOf(decryptionKeyAgentId);
                                        jedis.set(dbPrefix() + "decryption-key-agent:" + tenantId + ":" + thingId, response.body().string());
                                        jedis.rpush(dbPrefix() + TENANT_DECRYPTION_KEY_AGENT_LIST_KEY, thingId);
                                    }
                                } else {
                                    log.error("Could not receive decryption key agent from {}. Status code: {}", decryptionKeyAgentUrl, response.code());
                                }
                                response.body().close();
                            } catch (IOException e) {
                                log.error("Could not receive decryption key agent from {}. {}", decryptionKeyAgentUrl, e.getMessage());
                            }
                        }
                    }
                }
            }
        }
    }

    public void stop() {
        Spark.stop();
        jedisPool.destroy();
        if(updateTask != null) {
            updateTask.cancel();
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
