package sk.upjs.kopr.file_copy.client;

import sk.upjs.kopr.file_copy.FileInfo;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// trieda pre obnovenie stahovania, spravuje subor .progress.dat kde si uklada stav stahovania
public class ProgressInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final String PROGRESS_FILE_NAME = ".progress.dat";

    //"cesta" k suboru .progress.dat
    private final transient File progressFile;

    private Map<String, Long> progressMap;

    public transient long totalSize = 0;
    public transient int totalFiles = 0;

    private ProgressInfo(File destDir) {
        this.progressFile = new File(destDir, PROGRESS_FILE_NAME);
        this.progressMap = new ConcurrentHashMap<>();
    }


    //nacita zoznam suborov zo servera a porovna ho zo suborom .progress.dat
    public static ProgressInfo load(File destDir, List<FileInfo> serverFileList) {
        ProgressInfo info = new ProgressInfo(destDir);

        info.totalFiles = serverFileList.size();
        info.totalSize = serverFileList.stream().mapToLong(fe -> fe.size).sum();

        Map<String, Long> loadedMap = info.readFromFile();

        for (FileInfo entry : serverFileList) {
            long downloaded = loadedMap.getOrDefault(entry.relativePath, 0L);

            if (downloaded > entry.size) {
                downloaded = 0L;
            }

            File localFile = new File(destDir, entry.relativePath);
            if (!localFile.exists()) {
                downloaded = 0L;
            } else if (localFile.length() < downloaded) {
                downloaded = localFile.length();
            }

            if (downloaded == entry.size && localFile.length() == entry.size) {
                info.progressMap.put(entry.relativePath, entry.size);
            } else {
                info.progressMap.put(entry.relativePath, downloaded);
            }
        }
        info.save();
        return info;
    }


    //nacita mapu progresu z .progress.dat
    private Map<String, Long> readFromFile() {
        if (!progressFile.exists()) {
            System.out.println("Súbor s progresom neexistuje. Začína sa nové sťahovanie.");
            return new HashMap<>();
        }
        //ked subor existuje ale je prazdny povazujeme to za nový start nie za chybu
        if (progressFile.length() == 0) {
            System.err.println("Súbor s progresom je prázdny (pravdepodobne poškodený). Začína sa odznova.");
            return new HashMap<>();
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(progressFile))) {
            return (Map<String, Long>) ois.readObject();
        } catch (FileNotFoundException e) {
            return new HashMap<>();
        } catch (EOFException e) {
            System.err.println("Chyba: Súbor s progresom je poškodený. Začína sa odznova.");
            return new HashMap<>();
        } catch (Exception e) {
            System.err.println("Nepodarilo sa načítať súbor s progresom. Vytvára sa nový.");
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    //ulozenie aktualneho progresu do subory
    public synchronized void save() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(progressFile))) {
            //zapisujeme priamo do finalneho suboru
            oos.writeObject(new HashMap<>(this.progressMap));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void updateProgress(String relativePath, long downloadedBytes) {
        progressMap.put(relativePath, downloadedBytes);
    }


    //vrati pocet uz stiahnutych bajtov pre dany subor
    public long getDownloaded(String relativePath) {
        return progressMap.getOrDefault(relativePath, 0L);
    }

    //vrati celkovy pocet stiahnutych bajtov pre všetky súbory
    public long getTotalDownloaded() {
        return progressMap.values().stream().mapToLong(Long::longValue).sum();
    }

    //vrati pocet kompletne stiahnutych suborov
    public int getFilesCompleted(List<FileInfo> serverFileList) {
        int count = 0;
        for (FileInfo entry : serverFileList) {
            if (getDownloaded(entry.relativePath) == entry.size) {
                count++;
            }
        }
        return count;
    }
}