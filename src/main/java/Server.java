import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.SecureRandom;
import java.util.*;

public class Server {
    private static final long WAITING_TIME = 60000;
    private ServerSocket serverSocket;

    private Map<Integer, FileEntry> filesByID = new HashMap<>();
    private Map<ClientAddress, ClientInfo> activeClient = new HashMap<>();

    private Random rnd = new SecureRandom();

    private File stateFile;
    private PrintWriter stateWriter;

    public Server(File file) throws FileNotFoundException, UnsupportedEncodingException {
        stateFile = file;
        loadState();
        stateWriter = new PrintWriter(new FileOutputStream(file, true));
    }

    private void loadState() {
        Scanner in = null;
        try {
            in = new Scanner(stateFile);
            while (in.hasNext()) {
                FileEntry entry = new FileEntry();
                entry.clients = new HashSet<>();
                entry.id = in.nextInt();
                entry.name = in.next();
                entry.size = in.nextLong();

                filesByID.put(entry.id, entry);
            }
            in.close();
        } catch (FileNotFoundException ignored) {
        }
    }

    public static void main(String[] args) throws IOException {
        new Server(new File(args[0])).start();
    }

    public Thread start() throws IOException {
        serverSocket = new ServerSocket(Constants.SERVER_PORT);

        Thread thread = new Thread(() -> {
            try {
                catchSocket();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        thread.start();
        return thread;
    }

    public synchronized void stop() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Socket accept() throws IOException {
        try {
            return serverSocket.accept();
        } catch (SocketException e) {
            return null;
        }

    }

    private void catchSocket() throws IOException {
        while (true) {
            Socket socket = accept();
            if (socket != null) {
                handlingQuery(socket);
            } else {
                return;
            }
        }
    }

    private void handlingQuery(Socket socket) {
        try {
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            while (!socket.isClosed()) {
                byte operation = dis.readByte();
                if (operation == Constants.LIST_QUERY) {
                    handlingListQuery(dos);
                } else if (operation == Constants.UPLOAD_QUERY) {
                    handlingUploadQuery(dis, dos);
                } else if (operation == Constants.SOURCES_QUERY) {
                    handlingSourcesQuery(dis, dos);
                } else if (operation == Constants.UPDATE_QUERY) {
                    handlingUpdateQuery(dis, dos, socket);
                } else {
                    System.err.printf("Wrong query\n");
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

    private void handlingUploadQuery(DataInputStream dis, DataOutputStream dos) throws IOException {
        String name = dis.readUTF();
        Long size = dis.readLong();

        FileEntry newFile = new FileEntry();
        newFile.name = name;
        newFile.size = size;
        newFile.clients = new HashSet<>();
        newFile.id = rnd.nextInt();
        while (filesByID.containsKey(newFile.id)) {
            newFile.id = rnd.nextInt();
        }

        filesByID.put(newFile.id, newFile);

        stateWriter.println(newFile.id + " " + newFile.name + " " + newFile.size);
        stateWriter.flush();
        dos.writeInt(newFile.id);
    }

    private void handlingSourcesQuery(DataInputStream dis,DataOutputStream dos) throws IOException {
        int id = dis.readInt();

        FileEntry file = filesByID.get(id);

        ArrayList<ClientAddress> del = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        for (ClientAddress client : file.clients) {
            if (activeClient.get(client).lastUpdateTime < currentTime - WAITING_TIME) {
                del.add(client);
            }
        }

        for (ClientAddress client : del) {
            deleteClient(client);
        }

        dos.writeInt(file.clients.size());

        for (ClientAddress client : file.clients) {
            dos.write(client.ip);
            dos.writeShort(client.port);
        }
    }

    private void deleteClient(ClientAddress client) {
        if (activeClient.containsKey(client)) {
            ArrayList<Integer> oldClientsFiles = activeClient.get(client).files;
            activeClient.remove(client);

            for (Integer oldFiles : oldClientsFiles) {
                filesByID.get(oldFiles).clients.remove(client);
            }
        }
    }

    private void handlingUpdateQuery(DataInputStream dis, DataOutputStream dos, Socket socket) throws IOException {
        short seed_port = dis.readShort();
        int count = dis.readInt();

        byte[] ip = socket.getInetAddress().getAddress();

        ClientAddress newClient = new ClientAddress();
        newClient.ip = ip;
        newClient.port = seed_port;

        //System.err.println("Client Port update " + seed_port);

        deleteClient(newClient);

        ArrayList<Integer> clientsFilesId = new ArrayList<>();
        for (int i = 0; i < count; ++i) {
            Integer fileId = dis.readInt();
            clientsFilesId.add(fileId);

            //System.err.println(fileId);
            if (filesByID.containsKey(fileId)) {
                filesByID.get(fileId).clients.add(newClient);
            } else {
                socket.close();
                return;
            }
        }
        activeClient.put(newClient, new ClientInfo(clientsFilesId, System.currentTimeMillis()));

        //System.err.println("update");
        dos.writeBoolean(true);
    }

    private void handlingListQuery(DataOutputStream dos) throws IOException {
        dos.writeInt(filesByID.size());
        for (FileEntry entry : filesByID.values()) {
            dos.writeInt(entry.id);
            dos.writeUTF(entry.name);
            dos.writeLong(entry.size);
        }
    }

    public static class FileEntry {
        int id;
        String name;
        long size;

        Set<ClientAddress> clients = new HashSet<>();
    }

    private class ClientInfo {
        ArrayList<Integer> files;
        long lastUpdateTime;

        public ClientInfo(ArrayList<Integer> files, long lastUpdateTime) {
            this.files = files;
            this.lastUpdateTime = lastUpdateTime;
        }
    }
}
