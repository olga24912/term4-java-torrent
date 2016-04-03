import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

public class Client {
    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;

    private int port;

    private static final int PART_SIZE = (2 << 10);

    private static final int STAT_QUERY = 1;
    private static final int GET_QUERY = 2;

    private ArrayList< ArrayList<Boolean> > filesParts;

    public Client(String host, int port) throws IOException {
        this.port = port;
        socket = new Socket(host, port);
        dis = new DataInputStream(socket.getInputStream());
        dos = new DataOutputStream(socket.getOutputStream());
    }

    public void close() throws IOException {
        socket.close();
    }

    private void handlingQuery(Socket socket) {
        try {
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            while (!socket.isClosed()) {
                int operation = dis.readInt();
                if (operation == STAT_QUERY) {
                    handlingStatQuery(dis, dos);
                } else if (operation == GET_QUERY) {
                    handlingGetQuery(dis, dos);
                } else {
                    System.err.printf("Wrong query");
                }
            }
        } catch (IOException ignored) {
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void handlingStatQuery(DataInputStream dis, DataOutputStream dos) throws IOException {
        int id = dis.readInt();
        if (id >= filesParts.size()) {
            dos.write(0);
        } else {
            int cnt = 0;
            for (Boolean content: filesParts.get(id)) {
                if (content) {
                    ++cnt;
                }
            }
            dos.write(cnt);
            for (int i = 0; i < filesParts.get(id).size(); ++i) {
                if (filesParts.get(id).get(i)) {
                    dos.write(i);
                }
            }
        }
    }

    private void handlingGetQuery(DataInputStream dis, DataOutputStream dos) throws IOException {
        int id = dis.readInt();
        int part = dis.readInt();
    }
}
