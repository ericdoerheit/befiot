package de.ericdoerheit.befiot.core.test;

import de.ericdoerheit.befiot.core.*;
import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Pairing;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import static de.ericdoerheit.befiot.core.Util.powerset;
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
                EncryptionHeader header = encryptionKeyAgent.getHeader();

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


    @Ignore
    @Test
    public void printDecryptionKeyAgentSizes() {
        int[] numberOfDecryptionKeyAgents = new int[]{1, 10, 100, 1000, 10000};

        Pairing pairing = Util.getDefaultPairing();
        KeyAgentBuilder keyAgentBuilder;

        for (int i = 0; i < numberOfDecryptionKeyAgents.length; i++) {
            long start = System.currentTimeMillis();
            keyAgentBuilder = new KeyAgentBuilder(pairing, numberOfDecryptionKeyAgents[i]);
            DecryptionKeyAgent decryptionKeyAgent = keyAgentBuilder.getDecryptionKeyAgent(1);
            int size = Util.numberOfBytesOfListOfElements(decryptionKeyAgent.getPublicKey());
            long duration = System.currentTimeMillis() - start;
            log.debug("Size of public key (Max {} DKAs): {}B {}KB {}MB Calculation Time: {}ms", numberOfDecryptionKeyAgents[i],
                    size, size / 1024, size / (1024*1024), duration);
        }
    }

    @Test
    public void printPowerSet() {
        int[] testSet = new int[]{1, 2, 3, 4};
        List<int[]> pS = powerset(testSet);
        log.debug("{} Elements: {}", pS.size(), listAsString(pS));
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
