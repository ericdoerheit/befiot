package de.ericdoerheit.efiot.simulator;

import java.io.File;

/**
 * Created by ericdoerheit on 23/03/16.
 */
public class Util {
    public static void deleteFolder(File folder) {
        if(folder != null && folder.exists()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        deleteFolder(f);
                    } else {
                        f.delete();
                    }
                }
            }
            folder.delete();
        }
    }
}
