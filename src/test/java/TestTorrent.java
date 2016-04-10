import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static java.nio.file.Files.delete;
import static junit.framework.Assert.assertEquals;

public class TestTorrent {
    Path tmpDir;
    File file1;
    File fileState;

    Path tmpDirServer;
    File fileStateServer;

    Path tmpDir2;
    File file2;
    File fileState2;

    Server server;
    Client client1;
    Client client2;

    private static final int CLIENT1_PORT = 8090;
    private static final int CLIENT2_PORT = 8089;

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
    public void testNewFileAndList() throws IOException, InterruptedException {
        PrintWriter writer = new PrintWriter(file1);

        String fileEntry = "test   @";
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
                createClient1();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        thread1.start();

        thread1.join();

        Thread thread2 = new Thread(() -> {
            try {
                createClient2();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        thread2.start();

        thread2.join();
        server.stop();
    }

    @Test
    public void testDownload() throws IOException, InterruptedException {
        PrintWriter writer = new PrintWriter(file1);

        String fileEntry = "test   @";
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
                createClient1();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        thread1.start();

        //thread1.join();
        Thread thread2 = new Thread(() -> {
            try {
                downloadClient2();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread2.start();

        thread2.join();
        server.stop();
    }

    private void downloadClient2() throws IOException, InterruptedException {
        client2 = new Client("localhost", fileState2.getAbsolutePath());
        ArrayList<FileInfo> res = client2.list();
        client2.get(res.get(0).getId());

        client2.run(CLIENT2_PORT);
    }


    private void createClient2() throws IOException {
        client2 = new Client("localhost", fileState2.getAbsolutePath());
        ArrayList<FileInfo> res = client2.list();

        assertEquals(res.size(), 1);
        assertEquals(res.get(0).getName(), file1.getPath());
    }

    private void createClient1() throws IOException, InterruptedException {
        client1 = new Client("localhost", fileState.getAbsolutePath());
        client1.newFile(file1.getPath());
        client1.run(CLIENT1_PORT);
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
