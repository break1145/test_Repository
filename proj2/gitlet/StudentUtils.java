package gitlet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public class StudentUtils {

    public static void deleteAllFilesInDir(File dir) {
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            file.delete();
        }
    }

    /**
     * in the previous version, this function
     * is used to get the newest file in the .gitlet/commits
     */
    public static File getTheNewestFileInDir(File dir) throws IOException {
        // copied and adapted from https://www.baeldung.com/java-last-modified-file
        Path dirPath = dir.toPath();
        Optional<Path> opPath = Files.list(dirPath).filter(p -> !Files.isDirectory(p))
                .sorted((p1, p2) -> Long.valueOf(p2.toFile().lastModified())
                        .compareTo(p1.toFile().lastModified()))
                .findFirst();
        File theNewestFile;
        if (opPath.isPresent()) {
            theNewestFile = opPath.get().toFile();
        } else {
            throw new GitletException("find the newest commit failed");
        }

        return theNewestFile;
    }

    /**
     * we have already designed that the file will be put into
     * .gitlet/blobs/[sha1 of the file] directory
     * that is, the file/files in that directory, they all have the same content.
     * All we want is just content(after I have finished the following todo)
     *
     * // TODO: test30 bug here, f.txt and g.txt have the same content
     * //TODO: in Commit.java, there should hashmap storing the mapping of filename and fileBlobs(sha1)
     * //then we don't need to store blob in .gitlet/blobs/[sha1], we just name the blobs by its sha1
     *
     *
     *
     * @param dir the directory
     * @return the only file in the directory
     */
    public static File getTheOnlyFileInDir(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
                return files[0];
        } else {
            throw new GitletException("This is not a directory");
        }
    }
}
