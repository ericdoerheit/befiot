package de.ericdoerheit.befiot.registry;

import de.ericdoerheit.befiot.core.Util;
import de.ericdoerheit.befiot.core.data.TenantData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;

/**
 * Created by ericdorheit on 06/02/16.
 */
public class RegistryUtil {
    private final static Logger log = LoggerFactory.getLogger(RegistryUtil.class);

    public static final String REGISTRY_KEY_PREFIX = "registry:";
    public static final String TENANT_LIST_KEY = "tenants";
    public static final String TENANT_THING_ID_RANGE_OFFSET_KEY = "tenant-thing-id-range-offset";
    public static final String TENANT_KEY_PREFIX = "tenant:";

    private static final SecureRandom secureRandom = new SecureRandom();

    public static String randomToken() {
        String token = "";

        for (int i = 0; i < 10; i++) {
            token += new BigInteger(25, secureRandom).toString(32);
            if (i != 9) {
                token += "-";
            }
        }

        return new String(token);
    }

    public static String tenantKey(TenantData tenantData) {
        return tenantKey(tenantData.getHostname(), tenantData.getPort());
    }

    public static String tenantKey(String tenantHostname, Integer tenantPort) {
        return TENANT_KEY_PREFIX+Util.tenantId(tenantHostname, tenantPort);
    }
}
