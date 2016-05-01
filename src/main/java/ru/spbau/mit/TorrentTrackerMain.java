package ru.spbau.mit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public final class TorrentTrackerMain {
    private TorrentTrackerMain() {

    }

    public static void main(String[] args) throws IOException {
        String pathInfo = Paths.get(".").toAbsolutePath().toString();
        pathInfo += File.separator + Constants.NAME_OF_SERVER_STATE_FILE;

        new Server(new File(pathInfo)).start();
    }
}
