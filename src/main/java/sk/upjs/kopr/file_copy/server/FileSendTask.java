package sk.upjs.kopr.file_copy.server;

import sk.upjs.kopr.file_copy.FileInfo;
import sk.upjs.kopr.file_copy.FileRequest;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;

public class FileSendTask implements Runnable {

    private static final int BLOCK_SIZE = 16384; // 16 kB
    private final Socket socket;
    private final File sourceDirectory;
    private final BlockingQueue<String> taskQueue;

    public FileSendTask(Socket socket, File sourceDirectory, BlockingQueue<String> taskQueue) {
        this.socket = socket;
        this.sourceDirectory = sourceDirectory;
        this.taskQueue = taskQueue;
    }

    @Override
    public void run() {
        try (ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {

            while (true) {
                String relativePath = taskQueue.poll();
                if (relativePath == null) {
                    oos.writeUTF("DONE");
                    oos.flush();
                    break;
                }

                File fileToSend = new File(sourceDirectory, relativePath);

                oos.writeUTF("FILE");
                oos.writeObject(new FileInfo(relativePath, fileToSend.length()));
                oos.flush();

                //cakame na odpoved od klienta on povie odkial mame zacat posielat
                FileRequest fileRequest = (FileRequest) ois.readObject();

                if (fileRequest.length > 0) {
                    System.out.println(Thread.currentThread().getName() + " posiela " + relativePath
                            + " (offset: " + fileRequest.offset + ", len: " + fileRequest.length + ")");

                    try (RandomAccessFile raf = new RandomAccessFile(fileToSend, "r")) {
                        raf.seek(fileRequest.offset);

                        byte[] buffer = new byte[BLOCK_SIZE];
                        long sent = 0;
                        while (sent < fileRequest.length) {
                            //vypocitame kolko bajtov este mozeme precitat
                            int size = (int) Math.min(BLOCK_SIZE, fileRequest.length - sent);
                            int read = raf.read(buffer, 0, size);
                            if (read == -1) break;

                            oos.write(buffer, 0, read);
                            sent += read;
                        }
                        oos.flush();
                    }
                } else {
                    //length=0 znamena ze subor uz ma
                    System.out.println(Thread.currentThread().getName() + " preskakuje " + relativePath + " (už stiahnuté)");
                }
            }

        } catch (IOException e) {
            //klient spadne alebo sa odpojí
            System.err.println("Spojenie stratené (klient sa odpojil): " + socket.getInetAddress() + ": " + e.getMessage());
            //ak sa klient odpojil vlakno sa ukonci
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
            }
            System.out.println(Thread.currentThread().getName() + " ukončil úlohu.");
        }
    }
}