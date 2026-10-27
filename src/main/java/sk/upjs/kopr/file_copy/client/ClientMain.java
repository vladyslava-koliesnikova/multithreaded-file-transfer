package sk.upjs.kopr.file_copy.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import sk.upjs.kopr.file_copy.FileInfo;
import sk.upjs.kopr.file_copy.server.Server;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.*;


public class ClientMain extends Application {
    private static final String SERVER_HOST = "localhost";
    //manazersky port servera
    private static final int SERVER_PORT = Server.MANAGEMENT_PORT;
    //kam sa budu stahovat subory
    private static final File DEST_DIR = new File("./downloaded");
    //zoznam suborov stiahnuty zo servera
    private List<FileInfo> serverFileList;
    //objekt pre spravu progresu
    private ProgressInfo progressInfo;

    // GUI komponenty
    private Label statusLabel;
    private Label fileProgressLabel;
    private Label sizeProgressLabel;
    private ProgressBar fileProgressBar;
    private ProgressBar sizeProgressBar;
    private Button startButton;
    private TextField threadCountField;

    private ExecutorService executor;

    //planovac pre aktualizaciu GUI
    private ScheduledExecutorService guiUpdaterExecutor;

    @Override
    public void start(Stage primaryStage) {
        if (!DEST_DIR.exists()) {
            DEST_DIR.mkdirs();
        }

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        Label threadLabel = new Label("Počet vlákien:");
        threadCountField = new TextField("4");

        startButton = new Button("Štart / Pokračovať");
        statusLabel = new Label("Pripravený. Stlačte 'Štart'.");

        fileProgressLabel = new Label("Súbory (0/0)");
        fileProgressBar = new ProgressBar(0);
        fileProgressBar.setMaxWidth(Double.MAX_VALUE);

        sizeProgressLabel = new Label("Veľkosť (0.00 / 0.00 MB)");
        sizeProgressBar = new ProgressBar(0);
        sizeProgressBar.setMaxWidth(Double.MAX_VALUE);

        root.getChildren().addAll(threadLabel, threadCountField, startButton,
                statusLabel, fileProgressLabel, fileProgressBar,
                sizeProgressLabel, sizeProgressBar);

        startButton.setOnAction(e -> startCopyProcess());

        Scene scene = new Scene(root, 400, 300);
        primaryStage.setTitle("File copy");
        primaryStage.setScene(scene);

        primaryStage.setOnCloseRequest(e -> shutdown());
        primaryStage.show();
    }


    private void startCopyProcess() {
        int threadCount;
        try {
            threadCount = Integer.parseInt(threadCountField.getText());
            if (threadCount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            statusLabel.setText("Chyba: Neplatný počet vlákien.");
            return;
        }

        startButton.setDisable(true);
        threadCountField.setDisable(true);
        statusLabel.setText("Spúšťa sa");

        //synchronizator bude cakat na threadCount vlakien
        CountDownLatch latch = new CountDownLatch(threadCount);

        this.executor = Executors.newFixedThreadPool(threadCount);

        //komunikacia zo serverom bezi v samostatnom vlakne
        new Thread(() -> {
            try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                 ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {

                //poziadame o zoznam suborov
                oos.writeUTF("GET_INFO");
                oos.flush();
                this.serverFileList = (List<FileInfo>) ois.readObject();

                //nacitame lokalny progres a porovname ho so serverovym
                this.progressInfo = ProgressInfo.load(DEST_DIR, this.serverFileList);

                //aktualizujeme GUI s nacítanym progresom
                Platform.runLater(this::updateProgressUI);

                //start s N vlaknami
                oos.writeUTF("START " + threadCount);
                oos.flush();

                //zoznam pracovných portov
                List<Integer> workerPorts = (List<Integer>) ois.readObject();

                Platform.runLater(() -> statusLabel.setText("Pripájam workerov"));

                InetAddress inetAddress = InetAddress.getByName(SERVER_HOST);

                //spustiame N uloh FileReceiveTask v executore
                for (int port : workerPorts) {
                    executor.submit(new FileReceiveTask(inetAddress, port, DEST_DIR,
                            progressInfo, latch, statusLabel));
                }

                //ScheduledExecutorService na aktualizaciu GUI
                //"planovac" s jednym vlaknom
                this.guiUpdaterExecutor = Executors.newSingleThreadScheduledExecutor();

                //naplanujeme ulohu ktora sa spusti kazdych 500ms
                this.guiUpdaterExecutor.scheduleAtFixedRate(() -> {
                    Platform.runLater(() -> {
                        updateProgressUI();
                    });
                }, 0, 500, TimeUnit.MILLISECONDS); //hned, potom kazdych 500ms


                //cakame kym vsetky FileReceiveTask vlakna neskoncia
                latch.await(); //blokujuce cakanie

                executor.shutdown(); //vypneme executor pre stahovanie
                guiUpdaterExecutor.shutdown(); //vypneme executor pre GUI update

                Platform.runLater(this::updateProgressUI);
                //zmazanie .progress.dat
                try {
                    File progressFile = new File(DEST_DIR, ".progress.dat");
                    if (progressFile.exists()) {
                        Files.delete(progressFile.toPath());
                    }
                } catch (IOException e) {

                }
                Platform.runLater(() -> {
                    statusLabel.setText("Sťahovanie dokončené.");
                    startButton.setText("Hotovo");
                });

            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Chyba: Spojenie zlyhalo. Je server spustený?"));
                e.printStackTrace();
                shutdown();
            }
        }).start();
    }

    //aktualizacia ProgressBarov a Labelov.
    private void updateProgressUI() {
        if (progressInfo == null) return;

        long totalBytes = progressInfo.totalSize;
        long downloadedBytes = progressInfo.getTotalDownloaded();
        int totalFiles = progressInfo.totalFiles;
        int downloadedFiles = progressInfo.getFilesCompleted(serverFileList);

        fileProgressLabel.setText(String.format("Súbory (%d / %d)", downloadedFiles, totalFiles));
        if (totalFiles > 0) {
            fileProgressBar.setProgress((double) downloadedFiles / totalFiles);
        }

        sizeProgressLabel.setText(String.format("Veľkosť (%.2f / %.2f MB)",
                downloadedBytes / 1_000_000.0,
                totalBytes / 1_000_000.0));
        if (totalBytes > 0) {
            sizeProgressBar.setProgress((double) downloadedBytes / totalBytes);
        }
    }

    //ukoncenie aplikacie
    private void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            System.out.println("Požiadavka na vypnutie, prerušujem sťahovanie");
            //shutdownNow() posle interrupt(), ale vlakno FileReceiveTask je zablokovane na ois.read() a na interrupt() nereaguje
            executor.shutdownNow();
        }

        //vypynanie aj GUI updater ak bezi
        if (guiUpdaterExecutor != null && !guiUpdaterExecutor.isShutdown()) {
            guiUpdaterExecutor.shutdownNow();
        }

        Platform.exit();
        System.exit(0); //zatvori vsetky sockety
    }

    public static void main(String[] args) {
        launch(args);
    }
}