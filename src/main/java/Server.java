import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;

public class Server {
    private ServerSocket serverSocket;

    private static final int port = 8081;

    private static final int LIST_QUERY = 1;
    private static final int UPLOAD_QUERY = 2;
    private static final int SOURCES_QUERY = 3;
    private static final int UPDATE_QUERY = 4;

    private ArrayList<FileEntry> filesByID = new ArrayList<>();
    private Map<ClientAddress, ArrayList<Integer> > activeClient = new HashMap<>();

    public Thread start() throws IOException {
        serverSocket = new ServerSocket(port);

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
                int operation = dis.readInt();
                if (operation == LIST_QUERY) {
                    handlingListQuery(dos);
                } else if (operation == UPLOAD_QUERY) {
                    handlingUploadQuery(dis, dos);
                } else if (operation == SOURCES_QUERY) {
                    handlingSourcesQuery(dis, dos);
                } else if (operation == UPDATE_QUERY) {
                    handlingUpdateQuery(dis, dos, socket);
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

    private void handlingUploadQuery(DataInputStream dis, DataOutputStream dos) throws IOException {
        String name = dis.readUTF();
        Long size = dis.readLong();

        FileEntry newFile = new FileEntry();
        newFile.name = name;
        newFile.size = size;
        newFile.clients = new HashSet<>();
        newFile.id = filesByID.size();

        filesByID.add(newFile);

        dos.write(newFile.id);
    }

    private void handlingSourcesQuery(DataInputStream dis,DataOutputStream dos) throws IOException {
        int id = dis.readInt();

        FileEntry file = filesByID.get(id);

        dos.write(file.clients.size());

        for (ClientAddress client : file.clients) {
            dos.write(client.ip);
        }
    }

    private void handlingUpdateQuery(DataInputStream dis, DataOutputStream dos, Socket socket) throws IOException {
        short seed_port = dis.readShort();
        int count = dis.readInt();

        byte[] ip = socket.getInetAddress().getAddress();

        ClientAddress newClient = new ClientAddress();
        newClient.ip = ip;
        newClient.port = seed_port;

        ArrayList<Integer> oldClientsFiles = activeClient.get(newClient);
        activeClient.remove(newClient);

        for(Integer oldFiles: oldClientsFiles) {
            filesByID.get(oldFiles).clients.remove(newClient);
        }

        ArrayList<Integer> clientsFilesId = new ArrayList<>();
        for (int i = 0; i < count; ++i) {
            Integer fileId = dis.readInt();
            clientsFilesId.add(fileId);

            filesByID.get(fileId).clients.add(newClient);
        }
        activeClient.put(newClient, clientsFilesId);

        dos.writeBoolean(true);
    }

    private void handlingListQuery(DataOutputStream dos) throws IOException {
        dos.write(filesByID.size());
        for (int i = 0; i < filesByID.size(); ++i) {
            dos.write(filesByID.get(i).id);
            dos.writeUTF(filesByID.get(i).name);
            dos.writeLong(filesByID.get(i).size);
        }
    }

    public static class FileEntry {
        int id;
        String name;
        long size;

        Set<ClientAddress> clients = new HashSet<>();
    }

    public static class ClientAddress {
        short port;
        byte[] ip;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ClientAddress that = (ClientAddress) o;

            if (port != that.port) return false;
            return Arrays.equals(ip, that.ip);

        }

        @Override
        public int hashCode() {
            int result = (int) port;
            result = 31 * result + Arrays.hashCode(ip);
            return result;
        }
    }
}
