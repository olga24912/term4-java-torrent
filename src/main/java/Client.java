import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Client {
    public static final int SERVER_PORT = 8081;

    private static final int SLEEP_TIME = 1000;
    private File stateFile;

    private ServerSocket serverSocket;

    private int port;

    private Map<Integer, FileInfo> files;
    private String host;

    public Client(String host, String pathInfo) throws IOException {
        this.host = host;

        stateFile = new File(pathInfo);

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
            //System.err.println("white query " + Thread.currentThread().getName());
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
            Socket socket = new Socket(host, SERVER_PORT);
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            dos.writeByte(Constants.UPDATE_QUERY);
            dos.writeShort(port);
            dos.writeInt(files.size());

            for (Integer id : files.keySet()) {
                dos.writeInt(id);
            }

            socket.close();
            Thread.sleep(SLEEP_TIME);
        }
    }

    private ArrayList<FileInfo> sendListQuery() throws IOException {
        Socket socket = new Socket(host, SERVER_PORT);
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

        dos.writeByte(Constants.LIST_QUERY);
        int count = dis.readInt();

        ArrayList<FileInfo> filesOnServer = new ArrayList<>();

        for (int i = 0; i < count; ++i) {
            filesOnServer.add(FileInfo.fromServerInfo(dis.readInt(), dis.readUTF(), dis.readLong()));
        }

        socket.close();
        return filesOnServer;
    }

    public void close() throws IOException {
    }

    public void get(int id, String name) throws FileNotFoundException {
        FileInfo fileInfo = FileInfo.fromServerInfo(id, name, -1);
        files.put(id, fileInfo);
    }

    public int newFile(String name) throws IOException {
        Socket socket = new Socket(host, SERVER_PORT);
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

        FileInfo fileInfo = FileInfo.fromLocalFile(dis, dos, name);
        files.put(fileInfo.getId(), fileInfo);

        socket.close();
        return fileInfo.getId();
    }

    public ArrayList<FileInfo> list() throws IOException {
        return sendListQuery();
    }

    public void run(int port) throws IOException, InterruptedException {
        this.port = port;
        startSendUpdateQuery();
        startSeedingThread();
        //System.err.println("start run");
        ArrayList<FileInfo> fis = list();
        //System.err.println("start after list");

        for (FileInfo fi : fis) {
            //System.err.println(fi.getId());
            if (files.containsKey(fi.getId()) && files.get(fi.getId()).getSize() == -1) {
                files.put(fi.getId(),
                        FileInfo.fromServerInfo(fi.getId(), files.get(fi.getId()).getName(), fi.getSize()));
            }
        }
        while (true) {
            //System.err.println("start download file");
            for (Map.Entry<Integer, FileInfo> entry : files.entrySet()) {
                if (entry.getValue().getSize() != -1) {
                    //System.err.println(entry.getValue().getId() +  Thread.currentThread().getName());
                    download(entry.getValue().getId(), entry.getValue().getName(), entry.getValue().getSize());
                }
            }
            Thread.sleep(SLEEP_TIME);
        }
    }

    private void startSeedingThread() throws IOException {
        serverSocket = new ServerSocket(port);
        Thread thread = new Thread(() -> {
            try {
                catheSocket();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        thread.start();
    }

    private void download(int id, String name, long size) throws IOException {
        //System.err.println(id + " " + name + " " +  size);

        ArrayList<ClientAddress> clientsWithFile = sendSourcesQuery(id);

        //System.err.println("clients size: "  + clientsWithFile.size());
        FileInfo file = files.get(id);

        Collections.shuffle(clientsWithFile);
        for (ClientAddress currentClient : clientsWithFile) {
            Socket socket = new Socket(InetAddress.getByAddress(currentClient.getIp()), currentClient.getPort());
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            //System.err.println(InetAddress.getByAddress(currentClient.ip));

            ArrayList<Integer> parts = sendStatQuery(dis, dos, id);

            //System.err.println("Start save parts " + id);

            for (Integer partNum : parts) {
                if (file.needPart(partNum)) {
                    //System.err.println("need part " + partNum);
                    byte[] partEntry = sendGetQuery(dis, dos, file, partNum);
                    file.savePart(partEntry, partNum);
                    System.err.println("Save part " + partNum + " " + id);
                }
            }
            socket.close();
        }
    }

    private byte[] sendGetQuery(DataInputStream dis, DataOutputStream dos, FileInfo file, int partNum)
            throws IOException {
        //System.err.println("Get Query " + file.getId() + " " + partNum);

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

    private ArrayList<Integer> sendStatQuery(DataInputStream dis, DataOutputStream dos, int id)
            throws IOException {
        dos.writeByte(Constants.STAT_QUERY);
        dos.writeInt(id);

        ArrayList<Integer> parts = new ArrayList<>();

        //System.err.println("StatQuery");
        int count = dis.readInt();
        //System.err.println("parts count: " + count);
        for (int i = 0; i < count; ++i) {
            int partNum = dis.readInt();
            parts.add(partNum);
        }
        return parts;
    }

    private ArrayList<ClientAddress> sendSourcesQuery(int id) throws IOException {
        Socket socket = new Socket(host, SERVER_PORT);
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

        dos.writeByte(Constants.SOURCES_QUERY);
        dos.writeInt(id);

        //System.err.println("Send sources query");

        ArrayList<ClientAddress> clients = new ArrayList<>();
        int cnt = dis.readInt();
        //System.err.println("cnt: " + cnt);

        for (int i = 0; i < cnt; ++i) {
            ClientAddress clientAddress = new ClientAddress();
            dis.read(clientAddress.getIp());
            //System.err.println("ip: " + Arrays.toString(clientAddress.ip));
            clientAddress.setPort(dis.readShort());
            //System.err.println("port: " + clientAddress.port);
            clients.add(clientAddress);
        }

        socket.close();
        return clients;
    }

    //Query from other client
    private void handlingQuery(Socket socket) {
        try {
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            while (!socket.isClosed()) {
                int operation = dis.readByte();
                if (operation == Constants.STAT_QUERY) {
                    handlingStatQuery(dis, dos);
                } else if (operation == Constants.GET_QUERY) {
                    handlingGetQuery(dis, dos);
                } else {
                    System.err.println("Wrong query " + String.format("%x", operation));
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
            dos.writeInt(0);
        } else {
            ArrayList<Integer> parts = files.get(id).getExistingParts();

            dos.writeInt(parts.size());

            for (Integer part : parts) {
                dos.writeInt(part);
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
        for (Map.Entry<Integer, FileInfo> entry : files.entrySet()) {
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
