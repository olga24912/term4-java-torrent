package ru.spbau.mit;

import org.apache.log4j.Logger;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public final class Main {
    private static final Logger LOG = Logger.getLogger(Main.class);

    private static Client client;
    private static JFileChooser chooserUploadFile;
    private static DownloadPane downloadPane;

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        String pathInfo = Paths.get(".").toAbsolutePath().toString();
        pathInfo += File.separator + Constants.NAME_OF_CLIENT_STATE_FILE;

        client = new Client("localhost", pathInfo);

        final JFrame frame = new JFrame("Torrent");
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

        JMenu menu = new JMenu("Menu");
        menu.add(itemDownload);
        menu.add(itemUpload);

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(menu);

        return menuBar;
    }
}
