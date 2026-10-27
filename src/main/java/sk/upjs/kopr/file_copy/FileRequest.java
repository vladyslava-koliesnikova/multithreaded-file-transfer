package sk.upjs.kopr.file_copy;

import java.io.Serializable;


public class FileRequest implements Serializable {
    private static final long serialVersionUID = -9175100941839168076L;
    public final long offset;
    public final long length;

    public FileRequest(long offset, long length) {
        this.offset = offset;
        this.length = length;
    }

    @Override
    public String toString() {
        return "FileRequest [offset=" + offset + ", length=" + length + "]";
    }
}