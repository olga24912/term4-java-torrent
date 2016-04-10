import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Client {
    public static final int SERVER_PORT = 8081;

    private File stateFile;

    private Socket socket;
    private ServerSocket serverSocket;
    private DataInputStream dis;
    private DataOutputStream dos;

    private int port;

    private static final int STAT_QUERY = 1;
    private static final int GET_QUERY = 2;

    private Map<Integer, FileInfo> files;

    public Client(String host, String path_info) throws IOException {
        socket = new Socket(host, SERVER_PORT);
        dis = new DataInputStream(socket.getInputStream());
        dos = new DataOutputStream(socket.getOutputStream());

        stateFile = new File(path_info);

        files = new HashMap<>();

        loadState();
    }

    public Thread startSendUpdateQuery() throws IOException {
        Thread thread = new Thread(() -> {
            try {
                try {
                    sendUpdateQuery();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        thread.start();
        return thread;
    }

    public Thread startCatheSocket() throws IOException {
        Thread thread = new Thread(() -> {
            try {
                catheSocket();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        thread.start();
        return thread;
    }

    private void catheSocket() throws IOException {
        while (true) {
            Socket socket = this.serverSocket.accept();
            if (socket != null) {
                handlingQuery(socket);
            } else {
                return;
            }
        }
    }

    private void sendUpdateQuery() throws IOException, InterruptedException {
        while (true) {
            dos.writeByte(Constants.UPDATE_QUERY);
            dos.writeShort(port);
            dos.writeInt(files.size());

            for (Integer id: files.keySet()) {
                dos.writeInt(id);
            }
            Thread.sleep(1000);
        }
    }

    private ArrayList<FileInfo> sendListQuery() throws IOException {
        dos.writeByte(Constants.LIST_QUERY);
        int count = dis.readInt();

        ArrayList<FileInfo> filesOnServer = new ArrayList<>();

        for (int i = 0; i < count; ++i) {
            filesOnServer.add(FileInfo.fromServerInfo(dis.readInt(), dis.readUTF(), dis.readLong()));
        }

        return filesOnServer;
    }

    public void close() throws IOException {
        socket.close();
    }


    public void get(int id) {
        files.put(id, null);
    }

    public int newFile(String name) throws IOException {
        FileInfo fileInfo = FileInfo.fromLocalFile(dis, dos, name);
        files.put(fileInfo.getId(), fileInfo);
        return fileInfo.getId();
    }

    public ArrayList<FileInfo> list() throws IOException {
        return sendListQuery();
    }


    public void run() throws IOException {
        startSendUpdateQuery();
    }



    //Client as client
    private void download(int id, String name, long size) throws IOException {
        if (!files.containsKey(id)) {;
            files.put(id, FileInfo.fromServerInfo(id, name, size));
        }

        ArrayList<ClientAddress> clientsWithFile = sendSourcesQuery(id);

        FileInfo file =  files.get(id);

        for (ClientAddress currentClient : clientsWithFile) {
            Socket socket = new Socket(InetAddress.getByAddress(currentClient.ip), currentClient.port);
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            ArrayList<Integer> parts = sendStatQuery(dis, dos, id);

            for (Integer partNum: parts) {
                if (file.needPart(partNum)) {
                    byte[] partEntry = sendGetQuery(dis, dos, file, partNum);
                    file.savePart(partEntry, partNum);
                }
            }
        }
    }

    private byte[] sendGetQuery(DataInputStream dis, DataOutputStream dos, FileInfo file, int partNum) throws IOException {
        dos.writeByte(2);
        dos.writeInt(file.getId());
        dos.writeInt(partNum);

        int partLen = file.getPartLength(partNum);

        byte[] partEntry = new byte[partLen];
        if (dis.read(partEntry) == partLen) {
            return partEntry;
        } else {
            return null;
        }
    }

    private ArrayList<Integer> sendStatQuery(DataInputStream dis, DataOutputStream dos, int id) throws IOException {
        dos.writeByte(1);
        dos.writeInt(id);

        ArrayList<Integer> parts = new ArrayList<>();

        int count = dis.readInt();
        for (int i = 0; i < count; ++i) {
            int partNum = dis.readInt();
            parts.add(partNum);
        }
        return parts;
    }

    private ArrayList<ClientAddress> sendSourcesQuery(int id) throws IOException {
        dos.writeByte(3);
        dos.writeInt(id);

        ArrayList<ClientAddress> clients = new ArrayList<>();
        int cnt = dis.readInt();

        for (int i = 0; i < cnt; ++i) {
            ClientAddress clientAddress = new ClientAddress();
            dis.read(clientAddress.ip);
            clientAddress.port = dis.readShort();

            clients.add(clientAddress);
        }
        return clients;
    }

    //Query from other client
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
        if (!files.containsKey(id)) {
            dos.write(0);
        } else {
            ArrayList<Integer> parts = files.get(id).getExistingParts();

            dos.write(parts.size());

            for (Integer part : parts) {
                dos.write(part);
            }
        }
    }

    private boolean handlingGetQuery(DataInputStream dis, DataOutputStream dos) throws IOException {
        int id = dis.readInt();
        int part = dis.readInt();

        return files.containsKey(id) && files.get(id).sendFilePart(part, dos);

    }

    public void saveState() throws FileNotFoundException {
        PrintWriter out = new PrintWriter(stateFile);
        out.print(files.size());
        out.print("\n");
        for (Map.Entry<Integer, FileInfo> entry: files.entrySet()) {
            if (entry.getValue() != null) {
                entry.getValue().writeInfo(out);
            } else {
                out.print(entry.getKey());
                out.print(" -1\n");
            }
        }
        out.close();
    }

    private void loadState() {
        try {
            Scanner in = new Scanner(stateFile);
            int cnt = in.nextInt();
            for (int i = 0; i < cnt; ++i) {
                FileInfo fi = FileInfo.fromStateFile(in);
                if (fi.getSize() < 0) {
                    files.put(fi.getId(), null);
                } else {
                    files.put(fi.getId(), fi);
                }
            }
            in.close();
        } catch (FileNotFoundException ignored) {
        }
    }
}
