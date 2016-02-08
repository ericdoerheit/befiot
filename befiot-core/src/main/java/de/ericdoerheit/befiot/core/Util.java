package de.ericdoerheit.befiot.core;

import de.ericdoerheit.befiot.core.data.TenantData;
import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Pairing;
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;

/**
 * Created by ericdorheit on 02/02/16.
 */
public class Util {
    private static final Logger log = LoggerFactory.getLogger(Util.class);

    final public static String CRYPTOGRAPHY_PROPERTIES_PATH = "/curves/a.properties";

    // TODO Create PairingParameters object and put all key-value pairs into this object (PairingParameter class has a method "put")
    public static Pairing getDefaultPairing() {
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
        return pairing;
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
}
