package de.ericdoerheit.befiot.core.data;

/**
 * Created by ericdorheit on 07/02/16.
 */
public class ThingData {
    private String token;
    private Integer id;

    public ThingData() {
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}
