package de.ericdoerheit.befiot.core;

import it.unisa.dia.gas.jpbc.Element;

/**
 * Created by ericdorheit on 08/02/16.
 */
public class EncryptionHeader {
    private Element c0Elem;
    private Element c1Elem;

    public EncryptionHeader() {
    }

    public Element getC0Elem() {
        return c0Elem;
    }

    public void setC0Elem(Element c0Elem) {
        this.c0Elem = c0Elem;
    }

    public Element getC1Elem() {
        return c1Elem;
    }

    public void setC1Elem(Element c1Elem) {
        this.c1Elem = c1Elem;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EncryptionHeader that = (EncryptionHeader) o;

        if (!c0Elem.isEqual(that.c0Elem)) return false;
        return c1Elem.isEqual(that.c1Elem);
    }
}
