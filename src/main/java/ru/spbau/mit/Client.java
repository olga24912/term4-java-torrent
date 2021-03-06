package ru.spbau.mit;

import org.apache.log4j.Logger;

import java.io.*;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Client {
    private static final Logger LOG = Logger.getLogger(Client.class);

    private static final int SLEEP_TIME_BETWEEN_DOWNLOADING_FILES = 100;
    private static final int SLEEP_TIME_BETWEEN_RECONNECT_TO_SERVER = 1000;

    private static final int COUNT_OF_TRYING_CONNECT_TO_SERVER = 3;

    private String stateFile;
    private ServerSocket serverSocket;

    private String downloadPath;

    private Map<Integer, FileInfo> files;
    private String trackerHost;

    private Boolean shutdownFlag = false;

    public Client(String host, String pathInfo) throws IOException {
        this.trackerHost = host;
        stateFile = pathInfo;
        files = new HashMap<>();

        loadState();
        createDownloadDir();
    }

    private void createDownloadDir() throws IOException {
        downloadPath = Paths.get(".").toAbsolutePath().toString();
        downloadPath = downloadPath.substring(0, downloadPath.length() - 2);
        downloadPath += File.separator + Constants.NAME_DOWNLOAD_DIR;

        createDir(downloadPath);
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
        LOG.debug("file for download id: " + id);
        files.put(fileInfo.getId(), fileInfo);
    }

    public int addNewFile(String name) throws IOException {
        Socket socket = connectToServer();
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

        FileInfo fileInfo = FileInfo.fromLocalFile(dis, dos, name);
        files.put(fileInfo.getId(), fileInfo);

        socket.close();
        return fileInfo.getId();
    }

    public ArrayList<FileInfo> getListOfFileOnServer() throws IOException {
        return sendListQuery();
    }

    public void run() throws IOException, InterruptedException {
        LOG.info("start run");
        serverSocket = new ServerSocket(genPort());
        LOG.debug("port: " + serverSocket.getLocalPort());

        startSeedingThread();
        startSendUpdateQuery();
        while (!shutdownFlag) {
            ArrayList<FileInfo> fis = getListOfFileOnServer();

            for (FileInfo fi : fis) {
                if (files.containsKey(fi.getId()) && files.get(fi.getId()).getSize() == -1) {
                    String newName = downloadPath + File.separator + fi.getId() + File.separator + fi.getName();
                    createDir(downloadPath + File.separator + fi.getId());
                    files.put(fi.getId(),
                            FileInfo.fromServerInfo(fi.getId(), newName, fi.getSize()));
                }
            }

            for (Map.Entry<Integer, FileInfo> entry : files.entrySet()) {
                if (entry.getValue().getSize() != -1) {
                    download(entry.getValue().getId());
                }
            }
            Thread.sleep(SLEEP_TIME_BETWEEN_DOWNLOADING_FILES);
        }
    }

    private void createDir(String path) throws IOException {
        File file = new File(path);
        if (!file.exists() || !file.isDirectory()) {
            Files.createDirectory(Paths.get(path));
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
                    sendUpdateQueries();
                } catch (InterruptedException e) {
                    LOG.trace(e.getMessage());
                }
            } catch (IOException e1) {
                LOG.trace(e1.getMessage());
            }
        });
        thread.start();
        return thread;
    }

    private void sendUpdateQueries() throws IOException, InterruptedException {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    sendUpdateQuery();
                } catch (IOException | InterruptedException e) {
                    LOG.trace(e.getMessage());
                }
            }
        }, 0, Constants.UPDATE_INTERVAL);
    }

    private void sendUpdateQuery() throws IOException, InterruptedException {
        Socket socket = connectToServer();
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

        dos.writeByte(Constants.UPDATE_QUERY);
        dos.writeShort(serverSocket.getLocalPort());
        dos.writeInt(files.size());

        for (Integer id : files.keySet()) {
            dos.writeInt(id);
        }

        socket.close();
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
            Socket socket = accept();
            if (socket != null) {
                handleQuery(socket);
            } else {
                return;
            }
        }
    }

    private Socket accept() {
        try {
            return this.serverSocket.accept();
        } catch (IOException e) {
            return null;
        }
    }

    private void download(int id) throws IOException {
        ArrayList<ClientAddress> clientsWithFile = sendSourcesQuery(id);

        FileInfo file = files.get(id);

        Collections.shuffle(clientsWithFile);
        for (ClientAddress currentClient : clientsWithFile) {
            if (currentClient.getPort() == serverSocket.getLocalPort()) {
                continue;
            }

            Socket socket;
            try {
                socket = connect(InetAddress.getByAddress(currentClient.getIp()).getHostAddress(),
                        currentClient.getPort());
            } catch (ConnectException e) {
                continue;
            }
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            ArrayList<Integer> parts = sendStatQuery(dis, dos, id);

            for (Integer partNum : parts) {
                if (file.needPart(partNum)) {
                    byte[] partEntry = sendGetQuery(dis, dos, file, partNum);
                    file.savePart(partEntry, partNum);
                    LOG.info("Save part " + partNum + " " + id);
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
            if (dis.read(clientAddress.getIp()) != Constants.CNT_BYTE_IN_IP) {
                LOG.warn("In client send source query read not 4 byte for ip");
            }
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
                    LOG.warn("Wrong query " + String.format("%x", operation));
                }
            }
        } catch (IOException e) {
            LOG.trace(e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e1) {
                LOG.trace(e1.getMessage());
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

    public void clearState() {
        try {
            Files.delete(Paths.get(stateFile));
        } catch (IOException e) {
            LOG.trace(e.getMessage());
        }
    }

    private Socket connectToServer() throws IOException {
        return connect(trackerHost, Constants.SERVER_PORT);
    }


    private Socket connect(String byAddress, int port) throws IOException {
        for (int i = 0; i < COUNT_OF_TRYING_CONNECT_TO_SERVER; ++i) {
            try {
                return new Socket(byAddress, port);
            } catch (ConnectException e) {
                try {
                    Thread.sleep(SLEEP_TIME_BETWEEN_RECONNECT_TO_SERVER);
                } catch (InterruptedException e1) {
                    LOG.trace("interrupt while reconnect to server");
                }
            }
        }
        return new Socket(byAddress, port);
    }

    public void stop() throws IOException {
        shutdownFlag = true;
        saveState();
        serverSocket.close();
    }

    public ArrayList<FileInfo> getListOfFile() {
        return files.entrySet().stream().map(Map.Entry::getValue).collect(Collectors.toCollection(ArrayList::new));
    }
}
