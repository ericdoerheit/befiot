package de.ericdoerheit.efiot.client;

import java.util.Map;
import java.util.Set;

/**
 * Created by ericdoerheit on 04/03/16.
 */
public interface IProtectionClient {

    /**
     *
     * @param message Message that will be protected
     * @param notValidAfter Time until message is valid (as unix timestamp milliseconds)
     * @param receivers List of receivers as strings (thingId@tenantId)
     * @return
     */
    public Message protectMessage(byte[] message, long notValidAfter, Set<String> receivers, boolean forceNewSessionKey);

    /**
     *
     * @param protectedMessage
     * @return plain message
     */
    public byte[] retrieveMessage(Message protectedMessage, boolean sessionKeyIsNew);
}
