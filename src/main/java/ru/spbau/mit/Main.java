package ru.spbau.mit;

import org.apache.log4j.Logger;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public final class Main {
    private static final Logger LOG = Logger.getLogger(Main.class);

    private static JFrame frame;

    private static Client client;
    private static JFileChooser chooserUploadFile;
    private static DownloadPane downloadPane;

    private static String pathInfo;
    private static String serverHost = "localhost";

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        pathInfo = Paths.get(".").toAbsolutePath().toString();
        pathInfo += File.separator + Constants.NAME_OF_CLIENT_STATE_FILE;

        client = new Client(serverHost, pathInfo);

        frame = new JFrame("Torrent");
        final JMenuBar menubar = buildMenuBar();

        chooserUploadFile = new JFileChooser();
        downloadPane = new DownloadPane(client);

        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                try {
                    client.saveState();
                } catch (IOException e) {
                    LOG.trace(e.getMessage());
                }

                System.exit(0);
            }
        });
        frame.setJMenuBar(menubar);
        frame.add(downloadPane);

        frame.pack();
        frame.setVisible(true);
    }

    private static JMenuBar buildMenuBar() {
        JMenuItem itemDownload = new JMenuItem("Available for download");
        JMenuItem itemUpload = new JMenuItem("Upload new file");
        JMenuItem itemServerHost = new JMenuItem("Choose server host");

        itemUpload.addActionListener(e -> {
            int returnVal = chooserUploadFile.showOpenDialog(new JPanel());

            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = chooserUploadFile.getSelectedFile();
                try {
                    client.addNewFile(file.getPath());
                } catch (IOException e1) {
                    LOG.trace(e1.getMessage());
                }
                downloadPane.update();
            }
        });

        itemDownload.addActionListener(e -> {
            final JFrame frame = new JFrame("Choose files");

            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

            try {
                frame.add(new ServerFilesPane(client, frame));
            } catch (IOException e1) {
                LOG.trace(e1.getMessage());
            }

            frame.pack();
            frame.setVisible(true);
        });

        itemServerHost.addActionListener(e -> {
            serverHost = JOptionPane.showInputDialog(frame, "Write server host");
            try {
                client.stop();
            } catch (IOException e1) {
                LOG.trace(e1.getMessage());
            }

            client.clearState();

            try {
                client = new Client(serverHost, pathInfo);
            } catch (IOException e1) {
                LOG.trace(e1.getMessage());
            }

            downloadPane.changeClient(client);
        });

        JMenu menu = new JMenu("Menu");
        menu.add(itemDownload);
        menu.add(itemUpload);
        menu.add(itemServerHost);

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(menu);

        return menuBar;
    }
}
