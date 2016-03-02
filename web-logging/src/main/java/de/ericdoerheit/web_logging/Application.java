package de.ericdoerheit.web_logging;

import spark.Spark;

/**
 * Created by ericdorheit on 10/02/16.
 */
public class Application {
    public static void main(String[] args) {

        int port = 3000;

        if (args.length == 1) {
            port = Integer.valueOf(args[0]);
        }

        Spark.port(port);
        Spark.threadPool(8);
        Spark.webSocket("/logs", WebSocketLogging.class);
        Spark.init();
    }
}
