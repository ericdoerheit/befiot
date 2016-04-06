package de.ericdoerheit.befiot.core.data;

import java.util.Arrays;

/**
 * Created by ericdorheit on 08/02/16.
 */
public class EncryptionHeaderData {
    private byte[] c0;
    private byte[] c1;

    public EncryptionHeaderData() {
    }

    public byte[] getC0() {
        return c0;
    }

    public void setC0(byte[] c0) {
        this.c0 = c0;
    }

    public byte[] getC1() {
        return c1;
    }

    public void setC1(byte[] c1) {
        this.c1 = c1;
    }

    @Override
    public String toString() {
        return "EncryptionHeaderData{" +
                "c0=" + Arrays.hashCode(c0) +
                ", c1=" + Arrays.hashCode(c1) +
                '}';
    }
}
