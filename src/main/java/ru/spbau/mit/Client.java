package ru.spbau.mit;

import java.io.*;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.*;

public class Client {
    private static final int SLEEP_TIME_BETWEEN_DOWNLOADING_FILES = 1000;
    private static final int SLEEP_TIME_BETWEEN_RECONNECT_TO_SERVER = 1000;

    private static final int COUNT_OF_TRYING_CONNECT_TO_SERVER = 3;

    private String stateFile;
    private ServerSocket serverSocket;

    private String downloadPath;

    private Map<Integer, FileInfo> files;
    private String trackerHost;

    public Client(String host, String pathInfo) throws IOException {
        this.trackerHost = host;
        stateFile = pathInfo;
        files = new HashMap<>();

        loadState();
        createDownloadDir();
    }

    private void createDownloadDir() {
        downloadPath = Paths.get(".").toAbsolutePath().toString();
        downloadPath = downloadPath.substring(0, downloadPath.length() - 2);
        downloadPath += File.separator + Constants.NAME_DOWNLOAD_DIR;
    }

    private void loadState() throws IOException {
        try {
            DataInputStream dis = new DataInputStream(new FileInputStream(stateFile));
            int cnt = dis.readInt();
            for (int i = 0; i < cnt; ++i) {
                FileInfo fi = FileInfo.fromStateFile(dis);
                if (fi.getSize() < 0) {
                    files.put(fi.getId(), FileInfo.fromServerInfo(fi.getId(), "", -1));
                } else {
                    files.put(fi.getId(), fi);
                }
            }
            dis.close();
        } catch (FileNotFoundException ignored) {
        }
    }

    public void addToDownloadFile(int id) throws FileNotFoundException {
        FileInfo fileInfo = FileInfo.fromServerInfo(id, "", -1);
        System.err.println("file for download id: " + id);
        files.put(fileInfo.getId(), fileInfo);
    }

    public int addNewFile(String name) throws IOException {
        Socket socket = connectToServer();
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

        FileInfo fileInfo = FileInfo.fromLocalFile(dis, dos, name);
        files.put(fileInfo.getId(), fileInfo);

        socket.close();
        System.err.println("add new file");
        return fileInfo.getId();
    }

    public ArrayList<FileInfo> getListOfFileOnServer() throws IOException {
        return sendListQuery();
    }

    public void run() throws IOException, InterruptedException {
        System.err.println("start run");
        serverSocket = new ServerSocket(genPort());
        System.err.println("port: " + serverSocket.getLocalPort());

        startSendUpdateQuery();
        startSeedingThread();
        ArrayList<FileInfo> fis = getListOfFileOnServer();

        for (FileInfo fi : fis) {
            if (files.containsKey(fi.getId()) && files.get(fi.getId()).getSize() == -1) {
                String newName = downloadPath + File.separator + fi.getName();
                System.err.println("new name: " + newName);
                files.put(fi.getId(),
                        FileInfo.fromServerInfo(fi.getId(), newName, fi.getSize()));
            }
        }
        while (true) {
            for (Map.Entry<Integer, FileInfo> entry : files.entrySet()) {
                System.err.println("entry key: " + entry.getKey());
                if (entry.getValue().getSize() != -1) {
                    download(entry.getValue().getId());
                }
            }
            Thread.sleep(SLEEP_TIME_BETWEEN_DOWNLOADING_FILES);
        }
    }

    private int genPort() {
        Random rnd = new Random();
        rnd.setSeed(System.currentTimeMillis());
        return (rnd.nextInt() % (Constants.MAX_PORT - Constants.MIN_PORT)
                + (Constants.MAX_PORT - Constants.MIN_PORT)) % (Constants.MAX_PORT - Constants.MIN_PORT)
                + Constants.MIN_PORT;
    }

    private ArrayList<FileInfo> sendListQuery() throws IOException {
        Socket socket = connectToServer();
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

    // use java.util.Timer
    private void sendUpdateQuery() throws IOException, InterruptedException {
        // add shutdown flag
        while (true) {
            Socket socket = new Socket(trackerHost, Constants.SERVER_PORT);
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            dos.writeByte(Constants.UPDATE_QUERY);
            dos.writeShort(serverSocket.getLocalPort());
            dos.writeInt(files.size());

            for (Integer id : files.keySet()) {
                dos.writeInt(id);
            }

            socket.close();
            //
            Thread.sleep(Constants.UPDATE_INTERVAL);
        }
    }

    private void startSeedingThread() throws IOException {
        Thread thread = new Thread(() -> {
            try {
                catchSocket();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        thread.start();
    }

    private void catchSocket() throws IOException {
        while (true) {
            Socket socket = this.serverSocket.accept();
            if (socket != null) {
                handleQuery(socket);
            } else {
                return;
            }
        }
    }

    // unused parameters
    private void download(int id) throws IOException {
        ArrayList<ClientAddress> clientsWithFile = sendSourcesQuery(id);

        FileInfo file = files.get(id);

        Collections.shuffle(clientsWithFile);
        for (ClientAddress currentClient : clientsWithFile) {
            Socket socket = new Socket(InetAddress.getByAddress(currentClient.getIp()), currentClient.getPort());
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            ArrayList<Integer> parts = sendStatQuery(dis, dos, id);

            for (Integer partNum : parts) {
                if (file.needPart(partNum)) {
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
        dos.writeByte(Constants.GET_QUERY);
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

        int count = dis.readInt();
        for (int i = 0; i < count; ++i) {
            int partNum = dis.readInt();
            parts.add(partNum);
        }
        return parts;
    }

    private ArrayList<ClientAddress> sendSourcesQuery(int id) throws IOException {
        Socket socket = new Socket(trackerHost, Constants.SERVER_PORT);
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

        dos.writeByte(Constants.SOURCES_QUERY);
        dos.writeInt(id);

        ArrayList<ClientAddress> clients = new ArrayList<>();
        int cnt = dis.readInt();

        for (int i = 0; i < cnt; ++i) {
            ClientAddress clientAddress = new ClientAddress();
            dis.read(clientAddress.getIp());
            clientAddress.setPort(dis.readShort());
            clients.add(clientAddress);
        }

        socket.close();
        return clients;
    }

    private void handleQuery(Socket socket) {
        try {
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            while (!socket.isClosed()) {
                int operation = dis.readByte();
                if (operation == Constants.STAT_QUERY) {
                    handleStatQuery(dis, dos);
                } else if (operation == Constants.GET_QUERY) {
                    handleGetQuery(dis, dos);
                } else {
                    System.err.println("Wrong query " + String.format("%x", operation));
                }
            }
        } catch (IOException ignored) {
            System.err.println("IOException in handle query");
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
                //throw new IllegalStateException(ignored);
            }
        }
    }

    private void handleStatQuery(DataInputStream dis, DataOutputStream dos) throws IOException {
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

    private boolean handleGetQuery(DataInputStream dis, DataOutputStream dos) throws IOException {
        int id = dis.readInt();
        int part = dis.readInt();

        return files.containsKey(id) && files.get(id).sendFilePart(part, dos);

    }

    public void saveState() throws IOException {
        DataOutputStream dos = new DataOutputStream(new FileOutputStream(stateFile));
        dos.writeInt(files.size());
        for (Map.Entry<Integer, FileInfo> entry : files.entrySet()) {
            if (entry.getValue() != null) {
                entry.getValue().writeInfo(dos);
            } else {
                dos.writeInt(entry.getKey());
                dos.writeLong(-1);
            }
        }
        dos.close();
    }

    private Socket connectToServer() throws IOException {
        for (int i = 0; i < COUNT_OF_TRYING_CONNECT_TO_SERVER; ++i) {
            try {
                return new Socket(trackerHost, Constants.SERVER_PORT);
            } catch (ConnectException e) {
                try {
                    Thread.sleep(SLEEP_TIME_BETWEEN_RECONNECT_TO_SERVER);
                } catch (InterruptedException e1) {
                    System.err.println("interrupt while reconnect to server");
                }
            }
        }
        return new Socket(trackerHost, Constants.SERVER_PORT);
    }
}
