package de.ericdoerheit.befiot.registry.test;

import static org.junit.Assert.*;

import de.ericdoerheit.befiot.registry.RegistryUtil;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by ericdorheit on 06/02/16.
 */
public class RegistryUtilTest {
    private Logger log = LoggerFactory.getLogger(RegistryUtilTest.class);

    @Test
    public void printToken() {
        log.debug("Token: {}", RegistryUtil.randomToken());
    }
}
