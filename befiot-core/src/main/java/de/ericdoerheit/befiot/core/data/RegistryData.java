package de.ericdoerheit.befiot.core.data;

/**
 * Created by ericdorheit on 07/02/16.
 */
public class RegistryData {
    private Integer maximumTenants;
    private Integer maximumThings;

    public RegistryData() {
    }

    public Integer getMaximumTenants() {
        return maximumTenants;
    }

    public void setMaximumTenants(Integer maximumTenants) {
        this.maximumTenants = maximumTenants;
    }

    public Integer getMaximumThings() {
        return maximumThings;
    }

    public void setMaximumThings(Integer maximumThings) {
        this.maximumThings = maximumThings;
    }
}
