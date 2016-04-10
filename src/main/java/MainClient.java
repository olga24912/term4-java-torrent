import java.io.IOException;
import java.util.AbstractList;
import java.util.Objects;

public final class MainClient {
    private static final int HOST_ARG = 0;
    private static final int PATH_INFO_ARG = 1;
    private static final int QUERY_ARG = 2;

    private static final int NEW_FILE_PATH_ARG = 3;
    private static final int NEW_FILE_CNT_ARGS = 4;

    private static final int GET_FILE_ID_ARG = 3;
    private static final int GET_CNT_ARGS = 4;

    private static final int CNT_ARGS = 3;


    private MainClient() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < CNT_ARGS) {
            help();
        }
        String host = args[HOST_ARG];
        String path_info = args[PATH_INFO_ARG];
        String query = args[QUERY_ARG];

        Client client = new Client(host, path_info);

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

            client.get(id);
        } else if (Objects.equals(query, "LIST")) {
            AbstractList<FileInfo> files = client.list();
            for (FileInfo fi : files) {
                System.err.print(fi.getId());
                System.err.print(" ");
                System.err.print(fi.getSize());
                System.err.print(" ");
                System.err.print(fi.getName());
                System.err.print("\n");
            }
        } else if (Objects.equals(query, "RUN")) {
            client.run();
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