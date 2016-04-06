package de.ericdoerheit.befiot.client;

/**
 * Created by ericdoerheit on 04/03/16.
 */
public class SessionKey {
    private int sessionId;
    private byte[] sessionKey;
    private long notValidAfter;

    public SessionKey() {
    }

    public boolean validate(long timestamp) {
        return timestamp <= notValidAfter;
    }

    public int getSessionId() {
        return sessionId;
    }

    public void setSessionId(int sessionId) {
        this.sessionId = sessionId;
    }

    public byte[] getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(byte[] sessionKey) {
        this.sessionKey = sessionKey;
    }

    public long getNotValidAfter() {
        return notValidAfter;
    }

    public void setNotValidAfter(long notValidAfter) {
        this.notValidAfter = notValidAfter;
    }
}
