package ru.spbau.mit;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class FileInfo implements Serializable {
    private static final int PART_SIZE = 2048;

    private String name;
    private long size;
    private boolean[] parts;
    private int id;

    public static FileInfo fromLocalFile(DataInputStream dis, DataOutputStream dos, String name)
            throws IOException {
        RandomAccessFile file = new RandomAccessFile(name, "r");

        dos.writeByte(Constants.UPLOAD_QUERY);
        String fileName = name.substring(name.lastIndexOf('/') + 1, name.length());
        dos.writeUTF(fileName);
        dos.writeLong(file.length());

        FileInfo fileInfo = new FileInfo();

        fileInfo.size = file.length();
        fileInfo.name = name;
        fileInfo.id = dis.readInt();

        fileInfo.parts = new boolean[getPartsCount(fileInfo.size)];

        Arrays.fill(fileInfo.parts, true);

        file.close();
        return fileInfo;
    }

    public static FileInfo fromServerInfo(int id, String name, long size) throws FileNotFoundException {
        FileInfo fileInfo = new FileInfo();

        fileInfo.id = id;
        fileInfo.size = size;
        fileInfo.parts = new boolean[getPartsCount(fileInfo.size)];
        fileInfo.name = name;

        Arrays.fill(fileInfo.parts, false);
        return fileInfo;
    }

    // DataInputStream
    public static FileInfo fromStateFile(DataInputStream dis) throws IOException {
        FileInfo fi = new FileInfo();
        fi.id = dis.readInt();
        fi.size = dis.readLong();
        if (fi.size < 0) {
            return fi;
        }
        fi.name = dis.readUTF();

        int cnt = getPartsCount(fi.size);
        fi.parts = new boolean[cnt];
        for (int i = 0; i < cnt; ++i) {
            fi.parts[i] = (Objects.equals(dis.readUTF(), "1"));
        }

        return fi;
    }

    private static int getPartsCount(long size) {
        return (int) ((size + PART_SIZE - 1) / PART_SIZE);
    }

    public int getId() {
        return id;
    }

    public boolean needPart(Integer partNum) {
        return !parts[partNum];
    }

    public int getPartLength(int partNum) {
        if (partNum < parts.length - 1) {
            return PART_SIZE;
        }
        return (int) (size % PART_SIZE);
    }

    public void savePart(byte[] partEntry, int partNum) throws IOException {
        RandomAccessFile file = new RandomAccessFile(name, "w");
        file.seek(getPosOfPart(partNum));
        file.write(partEntry);
        parts[partNum] = true;
        file.close();
    }

    public long getPosOfPart(int partNum) {
        return partNum * ((long) PART_SIZE);
    }

    public ArrayList<Integer> getExistingParts() {
        ArrayList<Integer> parts = new ArrayList<>();
        for (int i = 0; i < this.parts.length; ++i) {
            if (this.parts[i]) {
                parts.add(i);
            }
        }
        return parts;
    }

    public boolean sendFilePart(int part, DataOutputStream dos) throws IOException {
        if (part < 0 || part >= parts.length || !parts[part]) {
            return false;
        }

        RandomAccessFile file = new RandomAccessFile(name, "r");

        file.seek(getPosOfPart(part));

        byte[] partEntry = new byte[getPartLength(part)];
        file.read(partEntry);
        dos.write(partEntry);

        file.close();
        return true;
    }


    public void writeInfo(DataOutputStream dos) throws IOException {
        dos.writeInt(id);
        dos.writeLong(size);
        dos.writeUTF(name);

        for (boolean part : parts) {
            dos.writeUTF(part ? "1" : "0");
        }
    }

    public long getSize() {
        return size;
    }

    public String getName() {
        return name;
    }
}
