package ru.spbau.mit;

import org.apache.log4j.Logger;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

public class ServerFilesPane extends JPanel implements ListSelectionListener, ActionListener {
    private static final Logger LOG = Logger.getLogger(ServerFilesPane.class);
    private static final String DOWNLOAD_FILES_STRING = "OK";

    private Client client;
    private JFrame frame;

    private JList<String> list;
    private DefaultListModel<String> listModel;

    private JButton okButton;

    private ArrayList<FileInfo> files = new ArrayList<>();
    private ArrayList<FileInfo> chosenFiles = new ArrayList<>();

    public ServerFilesPane(Client client, JFrame frame) throws IOException {
        super(new BorderLayout());

        this.client = client;
        this.frame = frame;

        listModel = new DefaultListModel<>();

        list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setSelectedIndex(0);
        list.addListSelectionListener(this);

        okButton = new JButton(DOWNLOAD_FILES_STRING);
        okButton.setActionCommand(DOWNLOAD_FILES_STRING);
        okButton.addActionListener(this);

        update();

        JScrollPane scrollPane = new JScrollPane(list);

        add(scrollPane, BorderLayout.CENTER);
        add(okButton, BorderLayout.SOUTH);
    }


    public void update() throws IOException {
        files = client.getListOfFileOnServer();

        int rowsCount = listModel.getSize();
        for (int i = rowsCount - 1; i >= 0; --i) {
            listModel.remove(i);
        }

        for (FileInfo file : files) {
            String fileName = file.getName();
            listModel.addElement(fileName);
        }
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        chosenFiles.clear();
        for (int i = e.getFirstIndex(); i <= e.getLastIndex(); ++i) {
            chosenFiles.add(files.get(i));
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        for (FileInfo chosenFile : chosenFiles) {
            try {
                client.addToDownloadFile(chosenFile.getId());
            } catch (FileNotFoundException e1) {
                LOG.trace(e1.getMessage());
            }
        }

        frame.dispose();
    }
}
