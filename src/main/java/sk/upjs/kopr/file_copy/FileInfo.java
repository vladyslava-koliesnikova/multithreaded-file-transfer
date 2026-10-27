package sk.upjs.kopr.file_copy;

import java.io.Serializable;

public class FileInfo implements Serializable {
    private static final long serialVersionUID = -1361912600329298754L;

    public final String relativePath;
    public final long size;

    public FileInfo(String relativePath, long size) {
        this.relativePath = relativePath;
        this.size = size;
    }
}
