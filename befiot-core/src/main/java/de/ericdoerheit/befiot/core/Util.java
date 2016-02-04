package de.ericdoerheit.befiot.core;

import it.unisa.dia.gas.jpbc.Pairing;
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * Created by ericdorheit on 02/02/16.
 */
public class Util {
    private Logger log = LoggerFactory.getLogger(Util.class);

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

    public static String smallByteHash(byte[] bytes) {
        if(bytes == null)
            return "null";

        String result = "";

        for(int i = 0; i < 3 && i < bytes.length; i++)
            result += Byte.toString(bytes[i]);

        return result;
    }
}
