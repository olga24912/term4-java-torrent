package ru.spbau.mit;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

public class Server {
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

    public static void main(String[] args) throws IOException {
        new Server(new File(args[0])).start();
    }

    public Thread start() throws IOException {
        serverSocket = new ServerSocket(Constants.SERVER_PORT);

        Thread thread = new Thread(() -> {
            try {
                catchSockets();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        thread.start();
        return thread;
    }

    private void catchSockets() throws IOException {
        while (true) {
            Socket socket = accept();
            if (socket != null) {
                handleQuery(socket);
            } else {
                return;
            }
        }
    }

    private Socket accept() throws IOException {
        try {
            return serverSocket.accept();
        } catch (SocketException e) {
            return null;
        }
    }

    private void handleQuery(Socket socket) {
        try {
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            while (!socket.isClosed()) {
                byte operation = dis.readByte();
                if (operation == Constants.LIST_QUERY) {
                    handleListQuery(dos);
                } else if (operation == Constants.UPLOAD_QUERY) {
                    handleUploadQuery(dis, dos);
                } else if (operation == Constants.SOURCES_QUERY) {
                    handleSourcesQuery(dis, dos);
                } else if (operation == Constants.UPDATE_QUERY) {
                    handleUpdateQuery(dis, dos, socket);
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

    private void handleUploadQuery(DataInputStream dis, DataOutputStream dos) throws IOException {
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

    private void handleSourcesQuery(DataInputStream dis, DataOutputStream dos) throws IOException {
        int id = dis.readInt();

        FileEntry file = filesByID.get(id);

        ArrayList<ClientAddress> del = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        del.addAll(file.clients.stream().
                filter(client -> activeClient.get(client).lastUpdateTime <
                        currentTime - Constants.UPDATE_TIMEOUT).
                collect(Collectors.toList()));

        del.forEach(this::deleteClient);

        dos.writeInt(file.clients.size());

        for (ClientAddress client : file.clients) {
            dos.write(client.getIp());
            dos.writeShort(client.getPort());
        }
    }

    private void handleUpdateQuery(DataInputStream dis, DataOutputStream dos, Socket socket) throws IOException {
        short seedPort = dis.readShort();
        byte[] ip = socket.getInetAddress().getAddress();

        ClientAddress newClient = new ClientAddress();
        newClient.setIp(ip);
        newClient.setPort(seedPort);

        deleteClient(newClient);

        int count = dis.readInt();
        ArrayList<Integer> clientsFilesId = new ArrayList<>();
        for (int i = 0; i < count; ++i) {
            Integer fileId = dis.readInt();
            clientsFilesId.add(fileId);

            if (filesByID.containsKey(fileId)) {
                filesByID.get(fileId).clients.add(newClient);
            } else {
                socket.close();
                return;
            }
        }
        activeClient.put(newClient, new ClientInfo(clientsFilesId, System.currentTimeMillis()));

        dos.writeBoolean(true);
    }

    private void handleListQuery(DataOutputStream dos) throws IOException {
        dos.writeInt(filesByID.size());
        for (FileEntry entry : filesByID.values()) {
            dos.writeInt(entry.id);
            dos.writeUTF(entry.name);
            dos.writeLong(entry.size);
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

    private void loadState() {
        Scanner in;
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

    public synchronized void stop() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class FileEntry {
        private int id;
        private String name;
        private long size;

        private Set<ClientAddress> clients = new HashSet<>();
    }

    private class ClientInfo {
        private ArrayList<Integer> files = new ArrayList<>();
        private long lastUpdateTime;

        ClientInfo(ArrayList<Integer> files, long lastUpdateTime) {
            this.files.addAll(files);
            this.lastUpdateTime = lastUpdateTime;
        }
    }
}
