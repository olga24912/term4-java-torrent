package ru.spbau.mit;

import org.apache.log4j.Logger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;

public class DownloadPane extends JPanel {
    private static final Logger LOG = Logger.getLogger(DownloadPane.class);
    private static final int TIME_BETWEEN_UPDATE = 1000;
    private static final int PROGRESS_BAR_SIZE = 100;

    private Client client;

    private JTable table;
    private DefaultTableModel model;

    private Thread updateThread;

    private ArrayList<JProgressBar> progressBars = new ArrayList<>();

    public DownloadPane(Client client) throws IOException {
        super(new GridLayout(1, 0));

        model = new DefaultTableModel() {
            private String[] header = {"file name", "progress"};

            @Override
            public int getColumnCount() {
                return header.length;
            }

            @Override
            public String getColumnName(int index) {
                return header[index];
            }
        };

        this.client = client;
        table = new JTable(model);

        table.getColumn("progress").setCellRenderer((table1, value, isSelected, hasFocus, row, column) -> {
            if (progressBars.size() <= row) {
                progressBars.add(new JProgressBar(0, PROGRESS_BAR_SIZE));
            }
            progressBars.get(row).setStringPainted(true);
            return progressBars.get(row);
        });

        JScrollPane scrollPane = new JScrollPane(table);

        add(scrollPane);

        updateThread = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean wasNotInterrupt = true;
                while (wasNotInterrupt) {
                    update();
                    try {
                        Thread.sleep(TIME_BETWEEN_UPDATE);
                    } catch (InterruptedException e) {
                        wasNotInterrupt = false;
                    }
                    if (Thread.interrupted()) {
                        wasNotInterrupt = false;
                    }
                }
            }
        });

        updateThread.start();

        new Thread(() -> {
            try {
                client.run();
            } catch (IOException | InterruptedException e) {
                LOG.trace(e.getMessage());
            }
        }).start();
    }


    public void update() {
        ArrayList<FileInfo> files = client.getListOfFile();

        int rowsCount = model.getRowCount();
        for (int i = rowsCount - 1; i >= 0; --i) {
            model.removeRow(i);
        }

        for (FileInfo file : files) {
            String fileName = file.getName();
            model.addRow(new Object[]{fileName});
            int cntPartHave = file.getExistingPartsCount();
            int allPart = file.getPartsCount();

            int row = model.getRowCount() - 1;
            int column = 1;
            TableCellRenderer renderer = table.getCellRenderer(row, column);
            JProgressBar progressBar = (JProgressBar) table.prepareRenderer(renderer, row, column);

            if (allPart == 0) {
                allPart = 1;
            }

            progressBar.setValue(cntPartHave * PROGRESS_BAR_SIZE / allPart);
        }
    }
}
