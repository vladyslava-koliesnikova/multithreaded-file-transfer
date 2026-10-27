package sk.upjs.kopr.file_copy.client;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sk.upjs.kopr.file_copy.FileInfo;


class ProgressInfoTest {

    @TempDir
    File testDir;

    private List<FileInfo> serverFileList;

    @BeforeEach
    void setUp() {
        serverFileList = new ArrayList<>();
        serverFileList.add(new FileInfo("subor1.txt", 1000));
        serverFileList.add(new FileInfo("adresar/subor2.bin", 5000));
    }

    @Test
    void testNoveStahovanie() {
        ProgressInfo info = ProgressInfo.load(testDir, serverFileList);

        assertEquals(2, info.totalFiles, "Celkový počet súborov by mal byť 2");
        assertEquals(6000, info.totalSize, "Celková veľkosť by mala byť 6000");
        assertEquals(0, info.getTotalDownloaded(), "Na začiatku by nemalo byť nič stiahnuté");
        assertEquals(0, info.getDownloaded("subor1.txt"), "subor1.txt by mal mať 0 stiahnuté");
    }


    @Test
    void testUlozenieANacitanieProgresu() throws IOException { // Pridané "throws IOException"
        ProgressInfo info1 = ProgressInfo.load(testDir, serverFileList);
        info1.updateProgress("subor1.txt", 500);
        info1.updateProgress("adresar/subor2.bin", 2000);
        info1.save();

        File subor1 = new File(testDir, "subor1.txt");
        subor1.createNewFile();
        try (RandomAccessFile raf1 = new RandomAccessFile(subor1, "rw")) {
            raf1.setLength(500);
        }

        File subor2 = new File(testDir, "adresar/subor2.bin");
        subor2.getParentFile().mkdirs(); // Vytvoríme aj adresár
        subor2.createNewFile();
        try (RandomAccessFile raf2 = new RandomAccessFile(subor2, "rw")) {
            raf2.setLength(2000);
        }

        ProgressInfo info2 = ProgressInfo.load(testDir, serverFileList);

        assertEquals(2500, info2.getTotalDownloaded(), "Celkovo stiahnuté by malo byť 2500");
        assertEquals(500, info2.getDownloaded("subor1.txt"), "subor1.txt by mal mať 500");
        assertEquals(2000, info2.getDownloaded("adresar/subor2.bin"), "subor2.bin by mal mať 2000");
        assertEquals(0, info2.getFilesCompleted(serverFileList), "Žiaden súbor by nemal byť kompletný");
    }

    @Test
    void testPoskodenySubor() throws IOException {
        File poskodenySubor = new File(testDir, "subor1.txt");
        poskodenySubor.createNewFile();
        try (RandomAccessFile raf = new RandomAccessFile(poskodenySubor, "rw")) {
            raf.setLength(100);
        }

        ProgressInfo klamlivyInfo = ProgressInfo.load(testDir, new ArrayList<>());
        klamlivyInfo.updateProgress("subor1.txt", 500);
        klamlivyInfo.save();

        ProgressInfo realnyInfo = ProgressInfo.load(testDir, serverFileList);

        assertEquals(100, realnyInfo.getDownloaded("subor1.txt"),
                "Progres by sa mal resetovať na skutočnú veľkosť súboru (100)");
        assertEquals(100, realnyInfo.getTotalDownloaded(), "Celkový progres by mal byť 100");
    }
}