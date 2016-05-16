package ru.spbau.mit;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;

public class DownloadPane extends JPanel {
    private static final int TIME_BETWEEN_UPDATE = 1000;

    private Client client;

    private JTable table;
    private DefaultTableModel model;

    private Thread updateThread;

    public DownloadPane(Client client) throws IOException {
        super(new GridLayout(1, 0));

        model = new DefaultTableModel() {
            String[] header = {"file name", "progress"};

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
        table.setPreferredScrollableViewportSize(new Dimension(500, 70));
        table.setFillsViewportHeight(true);

        table.getColumn("progress").setCellRenderer(new TableCellRenderer() {
            JProgressBar progressBar = new JProgressBar(0, 100);

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                progressBar.setStringPainted(true);
                return progressBar;
            }
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
                try {
                    client.run();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
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
            int cntPartHave = file.getExistingPartsCount(), allPart = file.getPartsCount();

            int row = model.getRowCount() - 1, column = 1;
            TableCellRenderer renderer = table.getCellRenderer(row, column);
            JProgressBar progressBar = (JProgressBar) table.prepareRenderer(renderer, row, column);

            if (allPart == 0) {
                allPart = 1;
            }

            progressBar.setValue(cntPartHave * 100/allPart);
        }
    }
}
