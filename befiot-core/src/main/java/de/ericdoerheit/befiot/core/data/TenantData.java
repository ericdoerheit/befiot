package de.ericdoerheit.befiot.core.data;

/**
 * Created by ericdorheit on 06/02/16.
 */
public class TenantData {
    private String hostname;
    private Integer port;
    private String token;
    private EncryptionKeyAgentData encryptionKeyAgentData;
    private Integer thingIdStart;
    private Integer numberOfThings;
    private Integer numberOfOwnThings;

    public TenantData() {
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public EncryptionKeyAgentData getEncryptionKeyAgentData() {
        return encryptionKeyAgentData;
    }

    public void setEncryptionKeyAgentData(EncryptionKeyAgentData encryptionKeyAgentData) {
        this.encryptionKeyAgentData = encryptionKeyAgentData;
    }

    public Integer getThingIdStart() {
        return thingIdStart;
    }

    public void setThingIdStart(Integer thingIdStart) {
        this.thingIdStart = thingIdStart;
    }

    public Integer getNumberOfThings() {
        return numberOfThings;
    }

    public void setNumberOfThings(Integer numberOfThings) {
        this.numberOfThings = numberOfThings;
    }

    public Integer getNumberOfOwnThings() {
        return numberOfOwnThings;
    }

    public void setNumberOfOwnThings(Integer numberOfOwnThings) {
        this.numberOfOwnThings = numberOfOwnThings;
    }
}
