package de.ericdoerheit.befiot.core;

import de.ericdoerheit.befiot.core.data.TenantData;
import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Pairing;
import it.unisa.dia.gas.jpbc.PairingParameters;
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory;
import it.unisa.dia.gas.plaf.jpbc.pairing.parameters.PropertiesParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Created by ericdorheit on 02/02/16.
 */
public class Util {
    private static final Logger log = LoggerFactory.getLogger(Util.class);

    final public static String CRYPTOGRAPHY_PROPERTIES_PATH = "/curves/a.properties";

    public static Pairing getDefaultPairing() {
        /*
        String path = "/tmp/curve.properties";
        try {
            File tmpFileOut = new File(path);
            FileOutputStream fos = new FileOutputStream(tmpFileOut);

            InputStream in = Util.class.getResourceAsStream(CRYPTOGRAPHY_PROPERTIES_PATH);
            byte[] tmpBytes = new byte[1024];
            while (in.read(tmpBytes) > 0) {
                fos.write(tmpBytes);
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Pairing pairing = PairingFactory.getPairing("/tmp/curve.properties");

        */

        PropertiesParameters properties = new PropertiesParameters();
        properties.put("type", "a");
        properties.put("q", "8780710799663312522437781984754049815806883199414208211028653399266475630880222957078625179422662221423155858769582317459277713367317481324925129998224791");
        properties.put("h", "12016012264891146079388821366740534204802954401251311822919615131047207289359704531102844802183906537786776");
        properties.put("r", "730750818665451621361119245571504901405976559617");
        properties.put("exp1", "107");
        properties.put("exp2", "159");
        properties.put("sign0", "1");
        properties.put("sign1", "1");

        return PairingFactory.getPairing(properties);
    }

    public static int numberOfBytesOfListOfElements(List<Element> elements) {
        int size = 0;

        for (Element element : elements) {
            size += element.getLengthInBytes();
        }

        return size;
    }

    public static String tenantId(String tenantHostname, Integer tenantPort) {
        return tenantHostname+":"+String.valueOf(tenantPort);
    }

    public static String tenantId(TenantData tenantData) {
        if(tenantData != null)
            return tenantId(tenantData.getHostname(), tenantData.getPort());
        return null;
    }

    public static List<int[]> powerset(int[] s) {
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
}
