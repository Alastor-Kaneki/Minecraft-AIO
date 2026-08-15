package dev.alastorkaneki.minecrafthub;

interface IPrivilegedFileService {
    boolean canRead(String path);
    String[] list(String path);
    boolean copyFile(String sourcePath, String destinationPath);
    boolean deletePath(String path);
    boolean makeDirectories(String path);
    String lastError();
}
