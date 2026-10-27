package sk.upjs.kopr.file_copy.server;

import sk.upjs.kopr.file_copy.FileInfo;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;

public class Server {

    //manazersky port, klient sa pripaja sem pre riadiace príkazy
    public static final int MANAGEMENT_PORT = 5000;

    //adresar ktory chceme zdielat
    public static final File SOURCE_DIRECTORY = new File("D:/test_filecopy");

    //zoznam vsetkych suborov v zdielanom adresari
    private List<FileInfo> fileList;

    //synchronizer
    private BlockingQueue<String> taskQueue;

    public Server() throws IOException {
        if (!SOURCE_DIRECTORY.exists() || !SOURCE_DIRECTORY.isDirectory()) {
            throw new IOException("Zdrojový adresár neexistuje: " + SOURCE_DIRECTORY.getAbsolutePath());
        }
        //naskenujeme adresar pri starte
        this.fileList = scanDirectory(SOURCE_DIRECTORY);
        System.out.println("Server naskenoval adresár. Nájdených " + fileList.size() + " súborov.");
    }


    //rekurzívne prejde cely adresar a vytvori zoznam suborov
    private List<FileInfo> scanDirectory(File rootDir) throws IOException {
        List<FileInfo> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(rootDir.toPath())) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                String relativePath = rootDir.toPath().relativize(path).toString();
                files.add(new FileInfo(relativePath, path.toFile().length()));
            });
        }
        return files;
    }


    public void start() throws IOException {
        //vytvorame manazersky socket
        try (ServerSocket managementSocket = new ServerSocket(MANAGEMENT_PORT)) {
            System.out.println("Server beží na manažérskom porte " + MANAGEMENT_PORT);
            System.out.println("Zdieľa sa adresár: " + SOURCE_DIRECTORY.getAbsolutePath());
            while (true) {
                //caka na pripojenie klienta pre riadenie
                Socket socket = managementSocket.accept();
                System.out.println("Manažérske spojenie od: " + socket.getInetAddress());
                //spusti obsluhu riadiaceho spojenia
                handleManagementConnection(socket);
            }
        }
    }

    private void handleManagementConnection(Socket socket) throws IOException {
        try (ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {

            //caka na prikaz GET_INFO
            String command = ois.readUTF();

            if ("GET_INFO".equals(command)) {
                System.out.println("Klient požiadal o zoznam súborov. Posielam " + fileList.size() + " položiek.");
                //posle klientovi cely zoznam
                oos.writeObject(this.fileList);
                oos.flush();
            }
            //caka na prikaz START N
            command = ois.readUTF();
            if (command.startsWith("START")) {
                int threadCount = Integer.parseInt(command.split(" ")[1]);
                System.out.println("Klient chce začať kopírovanie s " + threadCount + " vláknami.");

                //naplnime frontu uloh
                this.taskQueue = new LinkedBlockingQueue<>(this.fileList.stream().map(fe -> fe.relativePath).toList());

                //executor pre N vlakien
                ExecutorService executor = Executors.newFixedThreadPool(threadCount);
                List<Integer> workerPorts = new ArrayList<>();
                List<ServerSocket> workerSockets = new ArrayList<>();

                //vytvorame N novych ServerSocketov
                for (int i = 0; i < threadCount; i++) {
                    // Port 0 = OS pridelí náhodný voľný port
                    ServerSocket workerSS = new ServerSocket(0);
                    workerPorts.add(workerSS.getLocalPort());
                    workerSockets.add(workerSS);
                }

                //posielame klientovi zoznam portov na ktore ma pripojit
                oos.writeObject(workerPorts);
                oos.flush();

                //cakame kym klient pripoji na vsetky N porty
                System.out.println("Čakám na " + threadCount + " pracovných spojení");
                for (ServerSocket workerSS : workerSockets) {
                    Socket workerSocket = workerSS.accept();
                    System.out.println("Worker pripojený: " + workerSocket.getInetAddress() + ":" + workerSocket.getPort());

                    //pre kazde spojenie spustiame FileSendTask
                    executor.submit(new FileSendTask(workerSocket, SOURCE_DIRECTORY, taskQueue));
                    workerSS.close();
                }
                System.out.println("Všetci workeri pripojení. Proces kopírovania spustený.");
                executor.shutdown(); //executor prestane prijimat nove ulohy
            }

        } catch (Exception e) {
            System.err.println("Chyba v manažérskom spojení: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Server server = new Server();
        server.start();
    }
}