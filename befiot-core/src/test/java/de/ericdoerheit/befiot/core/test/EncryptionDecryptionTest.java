package de.ericdoerheit.befiot.core.test;

import de.ericdoerheit.befiot.core.DecryptionKeyAgent;
import de.ericdoerheit.befiot.core.EncryptionKeyAgent;
import de.ericdoerheit.befiot.core.KeyAgentBuilder;
import de.ericdoerheit.befiot.core.Util;
import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Pairing;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Created by ericdorheit on 03/02/16.
 */
public class EncryptionDecryptionTest {
    private Logger log = LoggerFactory.getLogger(EncryptionDecryptionTest.class);

    @Test
    public void encryptedKeyEqualsDecryptedKey() {
        KeyAgentBuilder keyAgentBuilder;
        EncryptionKeyAgent encryptionKeyAgent;
        ArrayList<DecryptionKeyAgent> decryptionKeyAgents = new ArrayList<DecryptionKeyAgent>();

        for (int n = 1; n < 5; n++) {
            keyAgentBuilder = new KeyAgentBuilder(Util.getDefaultPairing(), n);
            encryptionKeyAgent = keyAgentBuilder.getEncryptionKeyAgent();
            decryptionKeyAgents.clear();
            for (int i = 1; i <= n; i++) {
                decryptionKeyAgents.add(keyAgentBuilder.getDecryptionKeyAgent(i));
            }

            int[] ids = new int[n];
            for (int j = 0; j < ids.length; j++) {
                ids[j] = j+1;
            }
            log.debug(arrayAsString(ids));

            List<int[]> subsets = powerset(ids);

            for (int[] subset : subsets){
                encryptionKeyAgent.next(subset);

                Element encryptedKey = encryptionKeyAgent.getKey();
                List<Element> header = encryptionKeyAgent.getHeader();

                for (DecryptionKeyAgent decryptionKeyAgent : decryptionKeyAgents) {
                    Element decryptedKey = decryptionKeyAgent.getKey(header, subset);

                    if(Arrays.binarySearch(subset, decryptionKeyAgent.getId()) > 0) {
                        assertArrayEquals(decryptedKey.toBytes(), encryptedKey.toBytes());
                    } else {
                        // TODO: 04/02/16 assertNotEqual
                    }
                }
            }
        }
    }

    @Test
    public void testManualComputationOfBgwSchema() {

    }

    @Test
    public void testPowerSet() {
        int[] testSet = new int[]{1, 2, 3, 4};
        List<int[]> pS = powerset(testSet);
        log.debug("{} Elements: {}", pS.size(), listAsString(pS));
    }

    private List<int[]> powerset(int[] s) {
        List<int[]> result = new ArrayList<int[]>();

        if (s.length == 0) {
            result.add(s);
            return result;
        }

        else {
            int[] t = new int[s.length-1];
            // T = S \ {e}
            int e = s[0];

            for (int i = 1; i < s.length; i++) {
                t[i-1] = s[i];
            }

            result = powerset(t);

            // tmp = {x union {e} | x in powerset(t)}
            List<int[]> tmp = new ArrayList<int[]>();

            for(int[] x : result) {
                x = Arrays.copyOf(x, x.length+1);
                x[x.length-1] = e;
                tmp.add(x);
            }

            // return powerset(t) union tmp
            result.addAll(tmp);
            return result;
        }
    }

    private String listAsString(List<int[]> list) {
        String result = "{ ";

        int i = 0;
        for (int[] l : list) {
            result += arrayAsString(l);

            if(i < list.size()-1) {
                result += ", ";
            }

            i++;
        }

        return result + " }";
    }

    private String arrayAsString(int[] l) {
        String result = "{";

        for (int j = 0; j < l.length; j++) {
            result += l[j];

            if(j < l.length-1) {
                result += ", ";
            }
        }
        result += "}";

        return result;
    }
}
