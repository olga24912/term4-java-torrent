package ru.spbau.mit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;

public final class TorrentClientMain {
    // magic constant turn off
    private static final int QUERY_ARG = 0;
    private static final int SERVER_HOST_ARG = 1;

    private static final int NEW_FILE_PATH_ARG = 2;
    private static final int NEW_FILE_CNT_ARGS = 3;

    private static final int GET_FILE_ID_ARG = 2;
    private static final int GET_CNT_ARGS = 3;

    private static final int CNT_ARGS = 2;

    private TorrentClientMain() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < CNT_ARGS) {
            help();
        }
        String pathInfo = Paths.get(".").toAbsolutePath().toString();
        pathInfo += File.separator + Constants.NAME_OF_CLIENT_STATE_FILE;
        String query = args[QUERY_ARG];

        String host = args[SERVER_HOST_ARG];

        Client client = new Client(host, pathInfo);

        if (Objects.equals(query, "newfile")) {
            if (args.length < NEW_FILE_CNT_ARGS) {
                help();
            }

            String name = args[NEW_FILE_PATH_ARG];

            client.addNewFile(name);

        } else if (Objects.equals(query, "get")) {
            if (args.length < GET_CNT_ARGS) {
                help();
            }

            int id = Integer.parseInt(args[GET_FILE_ID_ARG]);
            client.addToDownloadFile(id);
        } else if (Objects.equals(query, "list")) {
            ArrayList<FileInfo> files = client.getListOfFileOnServer();
            System.err.println("List query");
            for (FileInfo fi : files) {
                System.out.println(fi.getId() + " " + fi.getSize() + " " + fi.getName());
            }
        } else if (Objects.equals(query, "run")) {
            client.run();
        } else {
            help();
        }
        client.saveState();
    }

    private static void help() {
        System.exit(1);
    }
}
