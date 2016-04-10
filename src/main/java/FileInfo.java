import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

public class FileInfo implements Serializable {
    private static final int PART_SIZE = (2 << 10);

    private RandomAccessFile file;
    private String name;
    private long size;
    private boolean[] parts;
    private int id;

    public static FileInfo fromLocalFile(DataInputStream dis, DataOutputStream dos, String name) throws IOException {
        FileInfo fileInfo = new FileInfo();

        fileInfo.file = new RandomAccessFile(name, "r");

        dos.writeByte(Constants.UPLOAD_QUERY);
        dos.writeUTF(name);
        dos.writeLong(fileInfo.file.length());

        fileInfo.size = fileInfo.file.length();
        fileInfo.name = name;
        fileInfo.id = dis.readInt();

        fileInfo.parts = new boolean[getPartsCount(fileInfo.size)];

        Arrays.fill(fileInfo.parts, true);

        return fileInfo;
    }

    public static FileInfo fromServerInfo(int id, String name, long size) throws FileNotFoundException {
        FileInfo fileInfo = new FileInfo();

        fileInfo.file = new RandomAccessFile(name, "rw");
        fileInfo.id  = id;
        fileInfo.size = size;
        fileInfo.parts = new boolean[getPartsCount(fileInfo.size)];
        fileInfo.name = name;

        Arrays.fill(fileInfo.parts, false);
        return fileInfo;
    }

    public static FileInfo fromStateFile(Scanner scan) throws FileNotFoundException {
        FileInfo fi = new FileInfo();
        fi.id = scan.nextInt();
        fi.size = scan.nextLong();
        if (fi.size < 0) {
            return fi;
        }
        fi.name = scan.next();
        String parts = scan.next();

        int cnt = getPartsCount(fi.size);
        fi.parts = new boolean[cnt];
        for (int i = 0; i < cnt; ++i) {
            fi.parts[i] = (parts.charAt(i) == '1');
        }

        fi.file = new RandomAccessFile(fi.name, "rw");

        return fi;
    }

    private static int getPartsCount(long size) {
        return (int) ((size + PART_SIZE - 1)/PART_SIZE);
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
        file.seek(getPosOfPart(partNum));
        file.write(partEntry);
        parts[partNum] = true;
    }

    public long getPosOfPart(int partNum) {
        return partNum*(long)PART_SIZE;
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

        file.seek(getPosOfPart(part));

        byte[] partEntry = new byte[getPartLength(part)];
        file.read(partEntry);
        dos.write(partEntry);
        return true;
    }


    public void writeInfo(PrintWriter wr) {
        wr.print(id);
        wr.print(" ");
        wr.print(size);
        wr.print(" ");
        wr.print(name);
        wr.print(" ");
        for (boolean part : parts) {
            wr.print(part ? 1 : 0);
        }
        wr.print("\n");
    }

    public long getSize() {
        return size;
    }

    public String getName() {
        return name;
    }
}
