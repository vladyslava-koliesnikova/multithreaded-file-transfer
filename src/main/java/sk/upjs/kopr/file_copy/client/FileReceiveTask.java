package sk.upjs.kopr.file_copy.client;

import javafx.application.Platform;
import javafx.scene.control.Label;
import sk.upjs.kopr.file_copy.FileInfo;
import sk.upjs.kopr.file_copy.FileRequest;

import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;


public class FileReceiveTask implements Runnable {
    private static final int BUFFER_SIZE = 16384; // 16 kB

    private final InetAddress inetAddress;
    private final int serverPort; //pracovny port
    private final File destDir;
    private final ProgressInfo progressInfo; //odkaz na zdielany manazer progresu

    //synchronizer, ked vlakno skoncí, zavola latch.countDown(), hlavne vlakno caka (latch.await()), kym vsetky vlakna neskoncia
    private final CountDownLatch latch;

    //aktualizacia stavu
    private final Label statusLabel;

    public FileReceiveTask(InetAddress inetAddress, int serverPort, File destDir,
                           ProgressInfo progressInfo, CountDownLatch latch, Label statusLabel) {
        this.inetAddress = inetAddress;
        this.serverPort = serverPort;
        this.destDir = destDir;
        this.progressInfo = progressInfo;
        this.latch = latch;
        this.statusLabel = statusLabel;
    }

    @Override
    public void run() {
        try (Socket socket = new Socket(inetAddress, serverPort);
             ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {

            while (true) {
                //caka na prikaz od servera FILE alebo DONE
                String command = ois.readUTF();

                if ("DONE".equals(command)) {
                    break;
                }

                if ("FILE".equals(command)) {
                    //server posiela informacie o subore
                    FileInfo entry = (FileInfo) ois.readObject();
                    File destFile = new File(destDir, entry.relativePath);

                    destFile.getParentFile().mkdirs();

                    //zistime kolko uz mame stiahnute
                    long downloaded = progressInfo.getDownloaded(entry.relativePath);
                    long toDownload = entry.size - downloaded;

                    //posleme serveru poziadavku (FileRequest) a pytame si iba tu cast ktorá nam chyba
                    oos.writeObject(new FileRequest(downloaded, toDownload));
                    oos.flush();

                    //prijimame data iba ak nejake chybaju
                    if (toDownload > 0) {
                        Platform.runLater(() -> statusLabel.setText("Sťahuje sa: " + entry.relativePath));

                        //po kolkych blokoch sa ulozi progres
                        final int SAVE_AFTER_N_BLOCKS = 3000;

                        try (RandomAccessFile raf = new RandomAccessFile(destFile, "rw")) {
                            raf.setLength(entry.size);
                            raf.seek(downloaded);

                            byte[] buffer = new byte[BUFFER_SIZE];
                            long received = 0;

                            //pocitadlo blokov od posledneho ulozenia
                            int blocksSinceLastSave = 0;

                            while (received < toDownload) {
                                int read = ois.read(buffer, 0, (int) Math.min(buffer.length, toDownload - received));
                                if (read == -1) break;

                                raf.write(buffer, 0, read);
                                received += read;
                                downloaded += read;
                                blocksSinceLastSave++;

                                //aktualizujeme progres iba v pamati
                                progressInfo.updateProgress(entry.relativePath, downloaded);

                                // ulozime progres na disk kazdych N blokov
                                if (blocksSinceLastSave >= SAVE_AFTER_N_BLOCKS) {
                                    progressInfo.save();
                                    blocksSinceLastSave = 0;
                                }
                            }
                        }
                        progressInfo.save();
                    }
                }
            }
        } catch (Exception e) {
            Platform.runLater(() -> statusLabel.setText("Chyba: Spojenie stratené."));
            e.printStackTrace();
        } finally {
            //sme skoncili
            latch.countDown();
            System.out.println(Thread.currentThread().getName() + " skončil.");
        }
    }
}