package controllers;

import models.Group;
import models.User;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by luka on 3.9.16..
 */
public class Logging {
    private static File[] logFiles;
    private static BufferedWriter[] writers;

    static boolean log(int level, User user, Group group, String message) {
        if(logFiles == null || writers == null)
            if(!initLogs())
                return false;
        try {
            writers[level].append(getLogString(user, group, message));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean initLogs() {
        File logDir = new File("logs/");
        if(!logDir.isDirectory() && !logDir.mkdir()) return false;
        Restrict[] perms = Restrict.values();
        logFiles = new File[perms.length];
        writers = new BufferedWriter[perms.length];
        for(int i=0; i<logFiles.length; i++) {
            int o = perms[i].ordinal();
            logFiles[o] = new File(logDir, "accesslog"+perms[i].requiredPermission);
            try {
                writers[o] = new BufferedWriter(new FileWriter(logFiles[o]));
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }

    private static String getLogString(User user, Group group, String message) {
        return user.id + "@" + (group==null?-1:group.id) + " - " + message + "\n";
    }

    static void flushWriters() {
        for(BufferedWriter bw : writers)
            try {
                bw.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
    }
}
