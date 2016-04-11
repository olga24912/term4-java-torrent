import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Scanner;

import static java.nio.file.Files.delete;
import static junit.framework.Assert.assertEquals;

public class TestTorrent {
    private Path tmpDir;
    private File file1;
    private File fileState;

    private Path tmpDirServer;
    private File fileStateServer;

    private Path tmpDir2;
    private File file2;
    private File fileState2;

    private Server server;
    private Client client1;
    private Client client2;
    private String fileEntry = "test   @";

    private static final int CLIENT1_PORT = 8090;
    private static final int CLIENT2_PORT = 8089;

    private static final int CLIENT3_PORT = 8091;
    private static final int CLIENT4_PORT = 8092;

    @Before
    public void create() throws IOException {
        tmpDir = Files.createTempDirectory("torrent1");
        file1 = new File(tmpDir.toString() + File.separator + "file1");
        fileState = new File(tmpDir.toString() + File.separator + "stateClient1");

        tmpDirServer = Files.createTempDirectory("torrent0");
        fileStateServer = new File(tmpDir.toString() + File.separator + "stateServer");

        tmpDir2 = Files.createTempDirectory("torrent2");
        file2 = new File(tmpDir.toString() + File.separator + "file");
        fileState2 = new File(tmpDir.toString() + File.separator + "stateClient2");
    }

    @Test
    public void testDownload() throws IOException, InterruptedException {
        PrintWriter writer = new PrintWriter(file1);

        writer.print(fileEntry);

        writer.close();

        Thread thread = new Thread(() -> {
            try {
                createServer();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        thread.start();

        Thread.sleep(100);

        Thread thread1 = new Thread(() -> {
            try {
                createClient1(CLIENT3_PORT);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });

        thread1.start();
        Thread.sleep(1000);

        Thread thread2 = new Thread(() -> {
            try {
                downloadClient2(CLIENT4_PORT);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread2.start();


        Thread.sleep(1000);

        thread1.interrupt();
        thread2.interrupt();
        server.stop();
        File file = new File (tmpDir2.toString() + File.separator + "down");

        Scanner in = new Scanner(file);
        String resS = in.nextLine();

        System.err.println(resS);

        assertEquals(fileEntry, resS);
    }


    @Test
    public void testNewFileAndList() throws IOException, InterruptedException {
        PrintWriter writer = new PrintWriter(file1);

        writer.print(fileEntry);

        writer.close();

        Thread thread = new Thread(() -> {
            try {
                createServer();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        thread.start();

        Thread.sleep(100);

        Thread thread1 = new Thread(() -> {
            try {
                createClient1(CLIENT1_PORT);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        thread1.start();

        Thread.sleep(100);

        Thread thread2 = new Thread(() -> {
            try {
                createClient2();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        thread2.start();

        thread2.join();

        thread1.interrupt();
        server.stop();
    }

    private void downloadClient2(int port) throws IOException, InterruptedException {
        client2 = new Client("localhost", fileState2.getAbsolutePath());
        ArrayList<FileInfo> res = client2.list();
        client2.get(res.get(0).getId(), tmpDir2.toString() + File.separator + "down");

        client2.run(port);
    }

    private void createClient2() throws IOException {
        client2 = new Client("localhost", fileState2.getAbsolutePath());
        ArrayList<FileInfo> res = client2.list();

        assertEquals(res.size(), 1);
        assertEquals(res.get(0).getName(), file1.getPath());
    }

    private void createClient1(int port) throws IOException, InterruptedException {
        client1 = new Client("localhost", fileState.getAbsolutePath());
        client1.newFile(file1.getPath());
        client1.run(port);
    }

    private void createServer() throws IOException {
        server = new Server(fileStateServer);
        server.start();
    }

    @After
    public void clean() throws IOException {
        deleteDir(tmpDir);
        deleteDir(tmpDir2);
        deleteDir(tmpDirServer);
    }

    private void deleteDir(Path dir) throws IOException {
        for (File c : (new File(dir.toString())).listFiles()) {
            delete(c.toPath());
        }

        delete(dir.toAbsolutePath());
    }
}
