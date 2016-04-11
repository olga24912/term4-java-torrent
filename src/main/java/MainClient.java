import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

public final class MainClient {
    private static final int HOST_ARG = 0;
    private static final int PATH_INFO_ARG = 1;
    private static final int QUERY_ARG = 2;

    private static final int NEW_FILE_PATH_ARG = 3;
    private static final int NEW_FILE_CNT_ARGS = 4;

    private static final int GET_FILE_ID_ARG = 3;
    private static final int GET_FILE_NAME_ARG = 4;
    private static final int GET_CNT_ARGS = 5;

    private static final int RUN_ARG_PORT = 3;
    private static final int RUN_CNT_ARGS = 4;

    private static final int CNT_ARGS = 3;


    private MainClient() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < CNT_ARGS) {
            help();
        }
        String host = args[HOST_ARG];
        String pathInfo = args[PATH_INFO_ARG];
        String query = args[QUERY_ARG];

        Client client = new Client(host, pathInfo);

        if (Objects.equals(query, "NEW_FILE")) {
            if (args.length < NEW_FILE_CNT_ARGS) {
                help();
            }

            String name = args[NEW_FILE_PATH_ARG];

            client.newFile(name);

        } else if (Objects.equals(query, "GET")) {
            if (args.length < GET_CNT_ARGS) {
                help();
            }

            int id = Integer.parseInt(args[GET_FILE_ID_ARG]);
            String name = args[GET_FILE_NAME_ARG];

            client.get(id, name);
        } else if (Objects.equals(query, "LIST")) {
            ArrayList<FileInfo> files = client.list();
            for (FileInfo fi : files) {
                System.out.println(fi.getId() + " " + fi.getSize() + " " + fi.getName());
            }
        } else if (Objects.equals(query, "RUN")) {
            if (args.length < RUN_CNT_ARGS) {
                help();
            }
            client.run(Integer.parseInt(args[RUN_ARG_PORT]));
        } else {
            help();
        }
        client.saveState();
    }

    private static void help() {
        System.err.print("args: <host:String>, <path to info file:String>, <LIST/GET/NEW_FILE/RUN:String>\n");
        System.exit(1);
    }
}
