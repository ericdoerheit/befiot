package de.ericdoerheit.web_logging;

import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by ericdorheit on 10/02/16.
 */
@WebSocket
public class WebSocketLogging {
    private final static Logger log = LoggerFactory.getLogger(WebSocketLogging.class);

    // Store sessions if you want to, for example, broadcast a message to all users
    private static final Queue<Session> sessions = new ConcurrentLinkedQueue<>();

    private Receiver receiver;

    public WebSocketLogging() {

        receiver = new Receiver(3001);
        Thread receiverThread = new Thread(receiver);
        receiverThread.start();

        Runtime.getRuntime().addShutdownHook( new Thread() {
            @Override public void run() {
                if (receiver != null) {
                    receiver.stop();
                }
            }
        } );
    }

    @OnWebSocketConnect
    public void connected(Session session) {
        sessions.add(session);
    }

    @OnWebSocketClose
    public void closed(Session session, int statusCode, String reason) {
        sessions.remove(session);
    }

    public void sendLogEntry(String logEntry) throws IOException {
        for (Session session : sessions) {
            session.getRemote().sendString(logEntry);
        }
    }

    private class Receiver implements Runnable {
        boolean running;
        int port;
        DatagramSocket socket;

        public Receiver(int port) {
            this.port = port;
            this.running = true;
        }

        @Override
        public void run() {
            log.info("Start Receiver");

            try {
                socket = new DatagramSocket(3001);
            } catch (IOException e) {
                e.printStackTrace();
            }

            while (running) {
                DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);

                try {
                    socket.receive(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                byte[] data = packet.getData();
                try {
                    String logEntry = new String(data, 0, packet.getLength(), "UTF-8");
                    log.debug(logEntry);
                    sendLogEntry(logEntry);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            socket.close();
        }

        public void stop() {
            running = false;
        }
    }
}
