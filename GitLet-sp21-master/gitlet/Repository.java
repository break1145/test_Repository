package gitlet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

import static gitlet.Utils.*;

import static gitlet.StudentUtils.*;

/**
 * Represents a gitlet repository.
 *
 * @author Linde
 */
public class Repository {
    /**
     * The current working directory.
     */
    public static final File CWD = new File(System.getProperty("user.dir"));

    /**
     * The .gitlet directory.
     */
    public static final File GITLET_DIR = join(CWD, ".gitlet");

    /**
     * The .gitlet/stageForAdd directory, where store the files readied for commit
     */
    public static final File GITLET_STAGE_FOR_ADD_DIR = join(GITLET_DIR, "stageForAdd");

    /**
     * The .gitlet/stageForRemove directory, where store the files readied for remove
     */
    public static final File GITLET_STAGE_FOR_REMOVE_DIR = join(GITLET_DIR, "stageForRemove");

    /**
     * The .gitlet/blobs directory, where store the files of different version
     */
    public static final File GITLET_BLOBS_DIR = join(GITLET_DIR, "blobs");

    /**
     * The .gitlet/commits directory, where store the serialized Commits
     */
    public static final File GITLET_COMMITS_DIR = join(GITLET_DIR, "commits");

    /**
     * The .gitlet/branches directory, where store the master, HEAD files
     */
    public static final File GITLET_BRANCHES_DIR = join(GITLET_DIR, "branches");

    /**
     * The .gitlet/branches/activeBranch file, where store the name of the active branch
     */
    public static final File GITLET_ACTIVE_BRANCH_FILE = join(GITLET_BRANCHES_DIR, "activeBranch");

    /**
     * The .gitlet/branches/master file, where store the sha1 value of master as file
     */
    private static File master_FILE = join(GITLET_BRANCHES_DIR, "master");

    /**
     * The .gitlet/branches/HEAD file, where store the sha1 value of HEAD as a file
     */
    public static final File HEAD_FILE = join(GITLET_BRANCHES_DIR, "HEAD");

    /**
     * if a commit has two parent, in log() we will print
     * the first seven digit of parent sha1
     */
    private static final int PARENT_SHA1_LEN = 7;

    /**
     * notice that we won't call add() then call commit(),
     * we will call setUpCommit() instead.
     */
    public static void init() {
        if (!GITLET_DIR.exists()) {
            GITLET_DIR.mkdir();
        } else {
            System.out.println(
                    "A Gitlet version-control system already exists in the current directory.");
            System.exit(0);
        }
        GITLET_STAGE_FOR_ADD_DIR.mkdir();
        GITLET_STAGE_FOR_REMOVE_DIR.mkdir();
        GITLET_BLOBS_DIR.mkdir();
        GITLET_COMMITS_DIR.mkdir();
        GITLET_BRANCHES_DIR.mkdir();
        try {
            GITLET_ACTIVE_BRANCH_FILE.createNewFile();
            master_FILE.createNewFile();
            HEAD_FILE.createNewFile();
            writeContents(GITLET_ACTIVE_BRANCH_FILE, "master");
            setUpFirstCommit();
        } catch (IOException excp) {
            throw new GitletException(excp.getMessage());
        }

    }

    private static void setUpFirstCommit() {
        String message = "initial commit";
        Commit commit = new Commit(message);
        String commitSha1 = serializeCommit(commit);
        setupBranch(commitSha1);

    }

    /**
     * add file to stagedForAddDir
     *
     * @param CWDFileName the file we want to add
     */
    public static void add(String CWDFileName) {
        File CWDFile = join(CWD, CWDFileName);
        if (!CWDFile.exists()) {
            System.out.println("File does not exist.");
            System.exit(0);
        }

        String CWDFileSha1 = sha1(readContents(CWDFile));
        Commit currentCommit = getCommitBySha1(getHeadCommitSha1());
        TreeMap<String, String> map = currentCommit.getMap();
        // If the current working version of the file is identical to the
        // version in the current commit, do not stage it to be added,
        if (map.containsKey(CWDFileName) && map.get(CWDFileName).equals(CWDFileSha1)) {
            // and remove it from the staging area if it is already
            // there (as can happen when a file is changed, added,
            // and then changed back to it’s original version).
            File fileInStagedForAdd = join(GITLET_STAGE_FOR_ADD_DIR, CWDFileName);
            if (fileInStagedForAdd.exists()) {
                fileInStagedForAdd.delete();
            }

            // The file will no longer be staged for removal (see gitlet rm),
            // if it was at the time of the command.
            File fileInStagedForRemove = join(GITLET_STAGE_FOR_REMOVE_DIR, CWDFileName);
            if (fileInStagedForRemove.exists()) {
                fileInStagedForRemove.delete();
            }
        } else {
            // if a file haven't been tracked
            // or a file is tracked, but it has been modified
            // we need to add it to staging area
            Path src = CWDFile.toPath();
            Path dest = join(GITLET_STAGE_FOR_ADD_DIR, CWDFile.getName()).toPath();
            try {
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException excp) {
                throw new GitletException(excp.getMessage());
            }
        }
    }

    /**
     * notice that this is the public method
     */
    public static void setUpCommit(String message) {
        if (message == null || message.equals("")) {
            System.out.println("Please enter a commit message.");
            System.exit(0);
        }
        checkIfStagedDirsAreAllEmpty();
        String HEADSha1 = getHeadCommitSha1();
        List<String> parentSha1List = new ArrayList<>();
        parentSha1List.add(HEADSha1);
        setUpCommit(message, parentSha1List);
    }

    /**
     * if it is the first commit, we will call commit constructor,
     * otherwise we will copy a commit then modify it.
     * Then delete files in stageForAdd directory.
     * After that, we will serialize it and put it in commitsDir,
     * and set HEAD point to active branch.
     * <p>
     * For example:
     * We initialize a Commit, whose sha1 value is a154ccd,
     * then we will serialize this commit, this serialized file
     * will be named after a154ccd, then we put it in .gitlet/commits
     */
    private static void setUpCommit(String message, List<String> parentSha1List) {
        // clone a commit then modify it
        Commit commit = getCommitBySha1(getHeadCommitSha1());
        commit.modifyCommit(message, parentSha1List,
                GITLET_STAGE_FOR_ADD_DIR, GITLET_BLOBS_DIR, GITLET_STAGE_FOR_REMOVE_DIR);
        String commitSha1 = serializeCommit(commit);
        setupBranch(commitSha1);
        deleteAllFilesInDir(GITLET_STAGE_FOR_ADD_DIR);
        deleteAllFilesInDir(GITLET_STAGE_FOR_REMOVE_DIR);
    }

    /**
     * serialize a Commit class in the GITLET_COMMITS_DIR/[first 2 sha1 digit]/[40 bit sha1 digit]
     * and return its sha1 value.
     * <p>
     * for example:
     * We serialize a Commit class, and get its sha1: a1fb321c,
     * this file's path will be .gitlet/commits/a1/a1fb321c
     *
     * @param commit the commit we want to serialize
     * @return the sha1 of the commit
     */
    private static String serializeCommit(Commit commit) {
        File commitFile = join(GITLET_COMMITS_DIR, "tempCommitName");
        writeObject(commitFile, commit);
        String commitSha1 = sha1(readContents(commitFile));

        File commitDir = join(GITLET_COMMITS_DIR, commitSha1.substring(0, 2));
        if (!commitDir.exists()) {
            commitDir.mkdir();
        }
        Path src = commitFile.toPath();
        Path dest = join(commitDir, commitSha1).toPath();
        try {
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            commitFile.delete();
        } catch (IOException excp) {
            throw new GitletException(excp.getMessage());
        }

        return commitSha1;
    }

    /**
     * set HEAD and active branch point to the newest commit.
     * recall that GITLET_ACTIVE_BRANCH_FILE store the name of the active branch.
     */
    private static void setupBranch(String theNewestCommitSha1) {
        String theNameOfTheActiveBranch = readContentsAsString(GITLET_ACTIVE_BRANCH_FILE);
        File activeBranchFile = join(GITLET_BRANCHES_DIR, theNameOfTheActiveBranch);
        writeContents(activeBranchFile, theNewestCommitSha1);
        writeContents(HEAD_FILE, theNewestCommitSha1);
    }

    /**
     * If the file exists in GITLET_STAGE_FOR_ADD_DIR, we remove it.
     * If the file is tracked in the current commit, stage it for removal
     * and remove the file from working directory if the user has not already done so
     * (do not remove it unless it is tracked in the current commit).
     *
     * @param targetFilename the name of the file that we want to remove
     */
    public static void remove(String targetFilename) {
        boolean findFileInStageForAddDir = false;
        // if filesInStageForAddDir is empty, it is ok, we don't need to do anything,
        // and then we move down to check if we need to delete file from current commit.
        for (String filename : Objects.requireNonNull(plainFilenamesIn(GITLET_STAGE_FOR_ADD_DIR))) {
            if (targetFilename.equals(filename)) {
                findFileInStageForAddDir = true;
                join(GITLET_STAGE_FOR_ADD_DIR, filename).delete();
            }
        }

        boolean findFileInCurrentCommit = false;
        Commit currentCommit = getCommitBySha1(getHeadCommitSha1());
        List<String> filenamesList = getFilenamesInCommit(currentCommit);
        if (filenamesList.contains(targetFilename)) {
            findFileInCurrentCommit = true;
            String blobSha1 = currentCommit.getMap().get(targetFilename);
            File blob = getBlob(blobSha1);
            Path src = blob.toPath();
            Path dest = join(GITLET_STAGE_FOR_REMOVE_DIR, targetFilename).toPath();
            try {
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException excp) {
                throw new GitletException(excp.getMessage());
            }

            if (join(CWD, targetFilename).exists()) {
                join(CWD, targetFilename).delete();
            }
        }

        if (!findFileInStageForAddDir && !findFileInCurrentCommit) {
            System.out.println("No reason to remove the file.");
            System.exit(0);
        }
    }

    public static void log() {
        String commitSha1 = getHeadCommitSha1();
        while (commitSha1 != null) {
            Commit commit = getCommitBySha1(commitSha1);
            List<String> parentSha1List = commit.getParentSha1List();
            printLogInfo(commitSha1, commit);
            // in log(), if a commit have multiple parents,
            // we only print the first parent
            if (!parentSha1List.isEmpty()) {
                commitSha1 = parentSha1List.get(0);
            } else {
                commitSha1 = null;
            }
        }

    }

    private static void printLogInfo(String commitSha1, Commit commit) {
        Date date = commit.getTimeStamp();
        List<String> parentSha1List = commit.getParentSha1List();
        String formattedDateString = formatDate(date);
        System.out.println("===");
        System.out.println("commit " + commitSha1);
        if (parentSha1List.size() == 2) {
            System.out.println("Merge: " + parentSha1List.get(0).substring(0, PARENT_SHA1_LEN)
                    + " " + parentSha1List.get(1).substring(0, PARENT_SHA1_LEN));
        }
        System.out.println("Date: " + formattedDateString);
        System.out.println(commit.getMessage());
        System.out.println();
    }

    public static void globalLog() {
        // since we don't care the order here, we can use File.list()
        for (String commitDirName : Objects.requireNonNull(GITLET_COMMITS_DIR.list())) {
            for (String commitSha1 : Objects.requireNonNull(plainFilenamesIn(join(GITLET_COMMITS_DIR, commitDirName)))) {
                Commit commit = getCommitBySha1(commitSha1);
                printLogInfo(commitSha1, commit);
            }
        }

    }

    public static void find(String targetMessage) {
        boolean findCommitWithTargetMessage = false;
        for (String commitDirName : Objects.requireNonNull(GITLET_COMMITS_DIR.list())) {
            for (String commitSha1 : Objects.requireNonNull(plainFilenamesIn(join(GITLET_COMMITS_DIR, commitDirName)))) {
                Commit commit = getCommitBySha1(commitSha1);
                if (commit.getMessage().equals(targetMessage)) {
                    findCommitWithTargetMessage = true;
                    System.out.println(commitSha1);
                }
            }
        }

        if (!findCommitWithTargetMessage) {
            System.out.println("Found no commit with that message.");
        }
    }

    public static void branch(String branchName) {
        File branchFile = join(GITLET_BRANCHES_DIR, branchName);
        if (!branchFile.exists()) {
            try {
                branchFile.createNewFile();
            } catch (IOException excp) {
                throw new GitletException(excp.getMessage());
            }
        } else {
            System.out.println("A branch with that name already exists.");
            System.exit(0);
        }

        String currentCommitSha1 = getHeadCommitSha1();
        writeContents(branchFile, currentCommitSha1);
    }

    public static void removeBranch(String branchName) {
        if (readContentsAsString(GITLET_ACTIVE_BRANCH_FILE).equals(branchName)) {
            System.out.println("Cannot remove the current branch.");
            System.exit(0);
        }

        File branchFile = join(GITLET_BRANCHES_DIR, branchName);
        if (branchFile.exists()) {
            branchFile.delete();
        } else {
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        }
    }


    /**
     * Copied from gitlet spec:
     * Takes the version of the file as it exists in the commit with the given id,
     * and puts it in the working directory, overwriting the version of the file
     * that’s already there if there is one. The new version of the file is not staged.
     */
    public static void checkoutCommitAndFilename(String targetCommitId, String targetFilename) {
        Commit targetCommit = getCommitBySha1(getCompletedSha1(targetCommitId));
        checkoutCommitAndFilename(targetCommit, targetFilename);
    }

    private static void checkoutCommitAndFilename(Commit targetCommit, String targetFilename) {
        if (targetCommit == null) {
            System.out.println("No commit with that id exists.");
            System.exit(0);
        }

        List<String> filenamesList = getFilenamesInCommit(targetCommit);
        if (!filenamesList.contains(targetFilename)) {
            System.out.println("File does not exist in that commit.");
            System.exit(0);
        }

        TreeMap<String, String> map = targetCommit.getMap();
        String blobSha1 = map.get(targetFilename);
        File blob = getBlob(blobSha1);
        Path src = blob.toPath();
        Path dest = join(CWD, targetFilename).toPath();
        try {
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException excp) {
            throw new GitletException(excp.getMessage());
        }

    }

    /**
     * Copied from gitlet spec:
     * Takes the version of the file as it exists in the head commit and
     * puts it in the working directory, overwriting the version of the file
     * that’s already there if there is one.
     * The new version of the file is not staged.
     */
    public static void checkoutFilename(String filename) {
        Commit headCommit = getCommitBySha1(getHeadCommitSha1());
        checkoutCommitAndFilename(headCommit, filename);
    }

    /**
     * Copied from gitlet spec:
     * Takes all files in the commit at the head of the given branch,
     * and puts them in the working directory, overwriting the versions
     * of the files that are already there if they exist.
     * Also, at the end of this command, the given branch will now be
     * considered the current branch (HEAD). Any files that are tracked
     * in the current branch but are not present in the checked-out branch
     * are deleted. The staging area is cleared, unless the checked-out
     * branch is the current branch
     */
    public static void checkoutBranchName(String targetBranchName) {
        File targetBranchFile = join(GITLET_BRANCHES_DIR, targetBranchName);
        if (!targetBranchFile.exists()) {
            System.out.println("No such branch exists.");
            System.exit(0);
        }

        String theNameOfTheActiveBranch = readContentsAsString(GITLET_ACTIVE_BRANCH_FILE);
        if (targetBranchName.equals(theNameOfTheActiveBranch)) {
            System.out.println("No need to checkout the current branch.");
            System.exit(0);
        }

        String targetCommitSha1 = readContentsAsString(targetBranchFile);
        Commit targetCommit = getCommitBySha1(targetCommitSha1);

        checkoutAllFilesInCommit(targetCommit);

        writeContents(GITLET_ACTIVE_BRANCH_FILE, targetBranchName);
        writeContents(HEAD_FILE, targetCommitSha1);
        deleteAllFilesInDir(GITLET_STAGE_FOR_ADD_DIR);
        deleteAllFilesInDir(GITLET_STAGE_FOR_REMOVE_DIR);
    }

    private static void checkoutAllFilesInCommit(Commit targetCommit) {
        checkIfUntrackedFileWillBeOverwrittenByCommit(targetCommit);

        // Any files that are tracked in the current branch
        // but are not present in the checked-out branch are deleted.
        Commit currentCommit = getCommitBySha1(getHeadCommitSha1());
        List<String> filenamesInCurrCommit = getFilenamesInCommit(currentCommit);
        List<String> filenamesInTargetCommit = getFilenamesInCommit(targetCommit);
        for (String filenameInCurrCommit : filenamesInCurrCommit) {
            if (!filenamesInTargetCommit.contains(filenameInCurrCommit)) {
                join(CWD, filenameInCurrCommit).delete();
            }
        }

        TreeMap<String, String> map = targetCommit.getMap();
        for (String filename : map.keySet()) {
            String fileSha1 = map.get(filename);
            File blob = getBlob(fileSha1);
            Path src = blob.toPath();
            Path dest = join(CWD, filename).toPath();
            try {
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException excp) {
                throw new GitletException(excp.getMessage());
            }
        }

    }

    public static void status() {
        String theNameOfTheActiveBranch = readContentsAsString(GITLET_ACTIVE_BRANCH_FILE);
        System.out.println("=== Branches ===");
        System.out.println("*" + theNameOfTheActiveBranch);
        for (String filename : Objects.requireNonNull(plainFilenamesIn(GITLET_BRANCHES_DIR))) {
            if (filename.equals(theNameOfTheActiveBranch)
                    || filename.equals("HEAD") || filename.equals("activeBranch")) {
                continue;
            }
            System.out.println(filename);
        }
        System.out.println();

        System.out.println("=== Staged Files ===");
        for (String filename : Objects.requireNonNull(plainFilenamesIn(GITLET_STAGE_FOR_ADD_DIR))) {
            System.out.println(filename);
        }
        System.out.println();

        System.out.println("=== Removed Files ===");
        for (String filename : Objects.requireNonNull(plainFilenamesIn(GITLET_STAGE_FOR_REMOVE_DIR))) {
            System.out.println(filename);
        }
        System.out.println();

        // TreeMap is sorted
        TreeMap<String, String> map = getModifiedButNotStagedFilesInCWD();
        System.out.println("=== Modifications Not Staged For Commit ===");
        for (String filename : map.keySet()) {
            System.out.println(filename + "(" + map.get(filename) + ")");
        }
        System.out.println();


        // The final category ("Untracked Files") is for files present in the
        // working directory but neither staged for addition nor tracked.
        // This includes files that have been staged for removal,
        // but then re-created without Gitlet’s knowledge.
        Commit currentCommit = getCommitBySha1(getHeadCommitSha1());
        List<String> filenamesInCommit = getFilenamesInCommit(currentCommit);
        System.out.println("=== Untracked Files ===");
        for (String CWDFilename : Objects.requireNonNull(plainFilenamesIn(CWD))) {
            // if a file is present in the CWD but neither stagedForAddDir nor tracked
            boolean condition1 = !filenamesInCommit.contains(CWDFilename)
                    && !join(GITLET_STAGE_FOR_ADD_DIR, CWDFilename).exists();
            // if there is a file both exist in CWD and stagedForRemoveDir
            boolean condition2 = join(GITLET_STAGE_FOR_REMOVE_DIR, CWDFilename).exists();
            if (condition1 || condition2) {
                System.out.println(CWDFilename);
            }
        }
        System.out.println();

    }

    /**
     * Copied from gitlet spec:
     * Checks out all the files tracked by the given commit.
     * Removes tracked files that are not present in that commit.
     * Also moves the current branch’s head to that commit node.
     *
     * @param uncompletedCommitId commitId can be abbreviated as for checkout
     */
    public static void resetWithUncompletedCommitId(String uncompletedCommitId) {
        String completedCommitId = getCompletedSha1(uncompletedCommitId);
        resetWithCompletedCommitId(completedCommitId);
    }

    private static void resetWithCompletedCommitId(String targetCommitId) {
        Commit targetCommit = getCommitBySha1(targetCommitId);
        if (targetCommit == null) {
            System.out.println("No commit with that id exists.");
            System.exit(0);
        }

        checkoutAllFilesInCommit(targetCommit);

        deleteAllFilesInDir(GITLET_STAGE_FOR_ADD_DIR);
        deleteAllFilesInDir(GITLET_STAGE_FOR_REMOVE_DIR);

        // Also moves the current branch’s head to that commit node.
        writeContents(HEAD_FILE, targetCommitId);
        String theNameOfActiveBranch = readContentsAsString(GITLET_ACTIVE_BRANCH_FILE);
        File activeBranchFile = join(GITLET_BRANCHES_DIR, theNameOfActiveBranch);
        writeContents(activeBranchFile, targetCommitId);
        // you may ask here we modify HEAD_FILE, but why we don't modify ACTIVE_BRANCH_FILE?
        // recall that if HEAD is in branch_A, and then it points to branch_B, in this case we
        // need to modify ACTIVE_BRANCH_FILE,
        // now what HEAD doing is to point to a previous commit of a branch,
        // it doesn't point to another branch, so we don't need to modify ACTIVE_BRANCH
    }

    /**
     * if we gonna switch to a certain commit, and that commit will overwrite
     * a file which is untracked by current commit, we will exit the entire program
     */
    private static void checkIfUntrackedFileWillBeOverwrittenByCommit(Commit targetCommit) {
        List<String> filenamesInTargetCommit = getFilenamesInCommit(targetCommit);
        Commit currentCommit = getCommitBySha1(getHeadCommitSha1());
        List<String> filenamesInCurrCommit = getFilenamesInCommit(currentCommit);

        for (String CWDFilename : Objects.requireNonNull(plainFilenamesIn(CWD))) {
            boolean condition1 = !filenamesInCurrCommit.contains(CWDFilename);
            boolean condition2 = filenamesInTargetCommit.contains(CWDFilename);
            // if a CWDFile is untracked by current commit
            // and the target commit will overwrite the CWDFile
            if (condition1 && condition2) {
                System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                System.exit(0);
            }
        }
    }

    public static void merge(String targetBranchName) {
        checkMergeFailureCases(targetBranchName);
        Commit targetCommit = getCommitAtTargetBranch(targetBranchName);
        Commit currentCommit = getCommitBySha1(getHeadCommitSha1());
        List<String> ancestorsListOfCurrCommit = getAncestorsOfCommit(currentCommit);
        List<String> ancestorsListOfTarCommit = getAncestorsOfCommit(targetCommit);
        if (ancestorsListOfCurrCommit.contains(getCommitSha1(targetCommit))) {
            System.out.println("Given branch is an ancestor of the current branch.");
            System.exit(0);
        }
        if (ancestorsListOfTarCommit.contains(getCommitSha1(currentCommit))) {
            checkoutBranchName(targetBranchName);
            System.out.println("Current branch fast-forwarded.");
            System.exit(0);
        }

        Commit spiltPointCommit =
                getTheLatestCommonAncestorCommit(ancestorsListOfCurrCommit, ancestorsListOfTarCommit);
        boolean hasMergeConflict =
                checkMergeCases(spiltPointCommit, currentCommit, targetCommit);
        /*
        if (spiltPointCommit.getTimeStamp() != currentCommit.getTimeStamp()
                && spiltPointCommit.getTimeStamp() != targetCommit.getTimeStamp()) {

            String theNameOfTheActiveBranch = readContentsAsString(GITLET_ACTIVE_BRANCH_FILE);
            setUpMergeConflictCommit("Merged " + targetBranchName
                    + " into " + theNameOfTheActiveBranch + ".",
                    getCommitSha1AtTargetBranch(targetBranchName));
        }
         */
        String theNameOfTheActiveBranch = readContentsAsString(GITLET_ACTIVE_BRANCH_FILE);
        setUpMergeConflictCommit("Merged " + targetBranchName
                        + " into " + theNameOfTheActiveBranch + ".",
                getCommitSha1AtTargetBranch(targetBranchName));
        if (hasMergeConflict) {
            System.out.println("Encountered a merge conflict.");
        }

    }

    /**
     * Merge commits differ from other commits: they record as parents both the head
     * of the current branch (called the first parent) and the head of the branch
     * given on the command line to be merged in.
     */
    private static void setUpMergeConflictCommit(String message, String secondParentSha1) {
        List<String> parentSha1List = new ArrayList<>();
        parentSha1List.add(getHeadCommitSha1());
        parentSha1List.add(secondParentSha1);
        setUpCommit(message, parentSha1List);
    }

    private static boolean checkMergeCases(Commit spiltPointCommit,
                                           Commit currentCommit, Commit targetCommit) {
        boolean hasMergeConflict = false;
        List<String> filenamesAtSpiltPoint = getFilenamesInCommit(spiltPointCommit);
        List<String> filenamesAtCurrCommit = getFilenamesInCommit(currentCommit);
        List<String> filenamesAtTargetCommit = getFilenamesInCommit(targetCommit);
        List<String> allFilenamesList = mergeThreeListIntoOne(filenamesAtSpiltPoint,
                filenamesAtCurrCommit, filenamesAtTargetCommit);

        for (String filename : allFilenamesList) {
            boolean targetFileIsSameAsSpiltFile =
                    compareTwoCommit(filename, targetCommit, spiltPointCommit);
            boolean currFileIsSameAsSpiltFile =
                    compareTwoCommit(filename, currentCommit, spiltPointCommit);
            boolean targetFileIsSameAsCurrFile =
                    compareTwoCommit(filename, targetCommit, currentCommit);

            // if targetFileIsSameAsSpiltFile is false, then we know
            // targetCommit contain the newest version of file
            if (!targetFileIsSameAsSpiltFile && currFileIsSameAsSpiltFile) {
                updateContentsAndStage(targetCommit, filename);
            } else if (!currFileIsSameAsSpiltFile && targetFileIsSameAsSpiltFile) {
                updateContentsAndStage(currentCommit, filename);
            } else if (!currFileIsSameAsSpiltFile && !targetFileIsSameAsCurrFile) {
                // if currCommit and targetCommit both contain the newest version of file,
                // and their content are different from each other, that means we meet conflict.

                // why we need to check if they have different content?
                // let's say at spiltPointCommit, A.txt content is "hello"
                // at targetCommit, A.txt is removed, at currCommit, A.txt is also removed
                // in this case, both targetCommit and currCommit are the
                // newest(be modified since spiltPoint), but they are both modified to the
                // same way (both be removed), in this case, we can't say we meet merge conflict
                hasMergeConflict = true;
                String contentsOfTargetFile = getContentsOfFile(targetCommit, filename);
                String contentsOfCurrFile = getContentsOfFile(currentCommit, filename);
                String resultContent = "<<<<<<< HEAD\n" + contentsOfCurrFile
                        + "=======\n" + contentsOfTargetFile + ">>>>>>>\n";
                File resultFile = join(CWD, filename);
                writeContents(resultFile, resultContent);
                add(filename);
            }
        }

        return hasMergeConflict;
    }

    /**
     * @param commit should be the only commit that contain the newest version of file
     */
    private static void updateContentsAndStage(Commit commit, String filename) {
        // let's say a file with name "A" exist in spiltPointCommit,
        // meanwhile a file name "A" is absent in targetCommit,
        // and a file name "A" exist in currentCommit,
        // that is: the file name "A" in the targetCommit(null) is the
        // newest version of the file.
        // since the newest version of the file is null,
        // we should remove the file with name "A"
        TreeMap<String, String> commitMap = commit.getMap();
        if (commitMap.containsKey(filename)) {
            File theNewestVersionOfFile = getBlob(commitMap.get(filename));
            writeContents(join(CWD, filename), readContentsAsString(theNewestVersionOfFile));
            add(filename);
        } else {
            // let's say currentCommit is the only commit that has the newest version of file,
            // and the file is null,
            // in this case the file does not exist in currentCommit,
            // we don't need to call remove()
            if (join(CWD, filename).exists()) {
                remove(filename);
            }
        }
    }

    /**
     * get the content of the file in a commit.
     * if the file does not exist in that commit, return empty string.
     */
    private static String getContentsOfFile(Commit commit, String filename) {
        TreeMap<String, String> commitMap = commit.getMap();
        if (!commitMap.containsKey(filename)) {
            return "";
        } else {
            String sha1 = commitMap.get(filename);
            return readContentsAsString(getBlob(sha1));
        }
    }

    /**
     * with no duplicate element
     */
    private static List<String> mergeThreeListIntoOne(List<String> list1,
                                                      List<String> list2, List<String> list3) {
        Set<String> set = new HashSet<>();
        set.addAll(list1);
        set.addAll(list2);
        set.addAll(list3);
        return new ArrayList<>(set);
    }

    // we compare givenCommit and benchmarkCommit,
    // if the content of B.txt in givenCommit is NOT the same as
    // the content of B.txt in benchmarkCommit, return false,
    // otherwise return true
    private static boolean compareTwoCommit(String filename, Commit givenCommit, Commit benchmarkCommit) {
        TreeMap<String, String> givenMap = givenCommit.getMap();
        TreeMap<String, String> spiltMap = benchmarkCommit.getMap();
        if (!givenMap.containsKey(filename) && !spiltMap.containsKey(filename)) {
            return true;
        }

        if (givenMap.containsKey(filename) && spiltMap.containsKey(filename)) {
            String givenFileSha1 = givenMap.get(filename);
            String spiltFileSha1 = spiltMap.get(filename);
            return givenFileSha1.equals(spiltFileSha1);
        }

        return false;
    }

    private static void checkMergeFailureCases(String targetBranchName) {
        if (Objects.requireNonNull(GITLET_STAGE_FOR_ADD_DIR.list()).length != 0
                || Objects.requireNonNull(GITLET_STAGE_FOR_REMOVE_DIR.list()).length != 0) {
            System.out.println("You have uncommitted changes.");
            System.exit(0);
        }

        File targetBranchFile = join(GITLET_BRANCHES_DIR, targetBranchName);
        if (!targetBranchFile.exists()) {
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        }

        String theNameOfActiveBranch = readContentsAsString(GITLET_ACTIVE_BRANCH_FILE);
        if (targetBranchName.equals(theNameOfActiveBranch)) {
            System.out.println("Cannot merge a branch with itself.");
            System.exit(0);
        }

        //checkIfStagedDirsAreAllEmpty();

        Commit commitAtTargetBranch = getCommitAtTargetBranch(targetBranchName);
        checkIfUntrackedFileWillBeOverwrittenByCommit(commitAtTargetBranch);

    }

    private static Commit getCommitAtTargetBranch(String targetBranchName) {
        return getCommitBySha1(getCommitSha1AtTargetBranch(targetBranchName));
    }

    private static String getCommitSha1AtTargetBranch(String targetBranchName) {
        File targetBranchFile = join(GITLET_BRANCHES_DIR, targetBranchName);
        return readContentsAsString(targetBranchFile);
    }

    /**
     * This NOT is similar to find the latest common ancestor of two linked-list.
     * merge() let a merged commit to have two parents, this will make the linked-list
     * into Graph.
     * <p>
     * Do notice that the value of parameter will be changed after this function is executed
     */
    private static Commit getTheLatestCommonAncestorCommit(List<String> ancestorsListOfCommit1,
                                                           List<String> ancestorsListOfCommit2) {

        // if you haven't called merge() before, the commit1 and commit2 will
        // only have one common ancestor: "initial commit".
        // but if have call merge(), they may have multiple common ancestor
        ancestorsListOfCommit1.retainAll(ancestorsListOfCommit2);
        List<String> commonAncestors = ancestorsListOfCommit1;

        // find the latest common ancestors
        // let's say there is ancestor A and ancestor B in commonAncestors,
        // if A has no ancestor, then we know A is not the latest ancestor,
        // then we remove A from commonAncestors
        while (commonAncestors.size() > 1) {
            // The later an element is added to the list,
            // the closer it is to the initial commit.
            // test36a-merge-parent2.in is a good example, try to
            // use this function to find the latest common ancestor
            // in that test
            String ancestorCommitSha1 = commonAncestors.get(commonAncestors.size() - 1);
            Commit ancestorCommit = getCommitBySha1(ancestorCommitSha1);
            List<String> ancestorsList = getAncestorsOfCommit(ancestorCommit);
            if (ancestorsList.size() == 0) {
                commonAncestors.remove(ancestorCommitSha1);
            }

        }
        String ancestorCommitSha1 = commonAncestors.get(0);
        return getCommitBySha1(ancestorCommitSha1);
    }

    private static List<String> getAncestorsOfCommit(Commit commit) {
        Set<String> set = new HashSet<>();
        getAncestorsOfCommit(commit, set);
        return new ArrayList<>(set);
    }

    private static void getAncestorsOfCommit(Commit commit, Set<String> set) {
        List<String> parentSha1List = commit.getParentSha1List();
        if (parentSha1List.size() == 0) {
            return;
        }
        String firstParentSha1 = parentSha1List.get(0);
        set.add(firstParentSha1);
        Commit firstParentCommit = getCommitBySha1(firstParentSha1);
        getAncestorsOfCommit(firstParentCommit, set);
        if (parentSha1List.size() == 2) {
            String secondParentSha1 = parentSha1List.get(1);
            set.add(secondParentSha1);
            Commit secondParentCommit = getCommitBySha1(secondParentSha1);
            getAncestorsOfCommit(secondParentCommit, set);
        }
        /*
        old version:
        List<String> parentSha1List = commit.getParentSha1List();
        for (String parentSha1 : parentSha1List) {
            set.add(parentSha1);
            commit = getCommitBySha1(parentSha1);
            getAncestorsOfCommit(commit, set);
        }
         */
    }

    private static List<String> getFilenamesInCommit(Commit commit) {
        TreeMap<String, String> map = commit.getMap();
        return new ArrayList<>(map.keySet());
    }

    /**
     * Copied from gitlet spec:
     * A file in the working directory is "modified but not staged" if it is:
     * Tracked in the current commit, changed in the working directory, but not staged; or
     * Staged for addition, but with different contents than in the working directory; or
     * Staged for addition, but deleted in the working directory; or
     * Not staged for removal, but tracked in the current commit and deleted from the working directory.
     *
     * @return the names of the files and the states of the files
     */
    private static TreeMap<String, String> getModifiedButNotStagedFilesInCWD() {

        // filename->"modified"     filename->"deleted"
        TreeMap<String, String> fileStateMap = new TreeMap<>();

        Commit currentCommit = getCommitBySha1(getHeadCommitSha1());

        // Tracked in the current commit, changed in the working directory, but not staged
        List<String> filenamesList = getFilenamesInCommit(currentCommit);
        TreeMap<String, String> commitMap = currentCommit.getMap();
        for (String filename : filenamesList) {
            File CWDFile = join(CWD, filename);
            if (!CWDFile.exists()) {
                continue;
            }
            String trackedFileSha1 = commitMap.get(filename);
            String CWDFileSha1 = sha1(readContents(join(CWD, filename)));
            if (!CWDFileSha1.equals(trackedFileSha1)) {
                if (!join(GITLET_STAGE_FOR_ADD_DIR, filename).exists()) {
                    fileStateMap.put(filename, "modified");
                }
            }
        }

        for (String filename : Objects.requireNonNull(plainFilenamesIn(GITLET_STAGE_FOR_ADD_DIR))) {
            if (join(CWD, filename).exists()) {
                // if the file is staged for addition,
                // but with different contents than in the working directory
                if (!sha1(readContents(join(GITLET_STAGE_FOR_ADD_DIR, filename)))
                        .equals(sha1(readContents(join(CWD, filename))))) {

                    fileStateMap.put(filename, "modified");
                }
            } else {
                // Staged for addition, but deleted in the working directory
                fileStateMap.put(filename, "deleted");
            }
        }

        // there is a file tracked in current commit, but it disappears in CWD,
        // and it is not in stageForRemoveDir
        for (String filename : filenamesList) {
            if (!join(CWD, filename).exists()
                    && !join(GITLET_STAGE_FOR_REMOVE_DIR, filename).exists()) {
                fileStateMap.put(filename, "deleted");
            }
        }

        return fileStateMap;
    }

    private static String getHeadCommitSha1() {
        return readContentsAsString(HEAD_FILE);
    }

    private static Commit getCommitBySha1(String commitSha1) {
        // the parentSha1 of initial commit is null
        // when we put the parentSha1 into this function,
        // we need to get null instead of throwing an exception,
        // so that we will know we meet the first commit
        if (commitSha1 == null) {
            return null;
        }

        if (commitSha1.length() < 40) {
            System.out.println("No commit with that id exists.");
            System.exit(0);
        }
        File file = join(GITLET_COMMITS_DIR, commitSha1.substring(0, 2), commitSha1);
        if (!file.exists()) {
            System.out.println("No commit with that id exists.");
            System.exit(0);
        }
        return readObject(file, Commit.class);
    }

    private static File getBlob(String blobSha1) {
        return join(GITLET_BLOBS_DIR, blobSha1);
    }

    private static void checkIfStagedDirsAreAllEmpty() {
        if (Objects.requireNonNull(GITLET_STAGE_FOR_ADD_DIR.list()).length == 0
                && Objects.requireNonNull(GITLET_STAGE_FOR_REMOVE_DIR.list()).length == 0) {
            System.out.println("No changes added to the commit.");
            System.exit(0);
        }
    }

    private static String formatDate(Date date) {
        // FYI: https://docs.oracle.com/javase/7/docs/api/java/util/Formatter.html
        return String.format("%1$ta %1$tb %1$te %1$tH:%1$tM:%1$tS %1$tY %1$tz", date);
        /*
            you can also use the following code to get the same output:
            SimpleDateFormat formatter =
                                 new SimpleDateFormat("E MMM dd hh:mm:ss yyyy Z");
            String formattedDateString = formatter.format(date);

            Since gitlet spec say I should use java.util.formatter,
            I didn't use SimpleDateFormat.
            if your Operating System's language is not English,
            it might have problem to display weekday and month,
            because weekday and month in String will be other language(e.g. Chinese),
            and the terminal may have problem to display that.

            If you are a Windows 10 user, you can right-click "Time" in the lower
            right corner of the screen, then click "Adjust Date/Time", and then click
            the second "Region" in the left column to modify the region format and
            change it to English
            */
    }

    /**
     * @param incompleteCommitId the abbreviated commit sha1
     */
    private static String getCompletedSha1(String incompleteCommitId) {
        String completedSha1 = null;
        int len = incompleteCommitId.length();
        if (len < 2) {
            System.out.println("No commit with that id exists.");
            System.exit(0);
        }
        String firstTwoSha1 = incompleteCommitId.substring(0, 2);
        File commitDir = join(GITLET_COMMITS_DIR, firstTwoSha1);
        List<String> filenamesInCommitDir = plainFilenamesIn(commitDir);
        if (filenamesInCommitDir == null) {
            System.out.println("No commit with that id exists.");
            System.exit(0);
        }

        /*
        let's say commitId is 3ac
        and in the .gitlet/commits/3a/ directory
        there are two files: 3acb12 and 3ac891
        3ac is not long enough to distinguish the two files,
        we don't know what commit should we pick.
         */
        boolean foundAFileSimilarToCommitId = false;
        for (String filename : filenamesInCommitDir) {
            if (filename.substring(0, len).equals(incompleteCommitId)) {
                // if it has already found a file similar to commit id,
                // and now it found again, that means there are at least
                // two files that are similar to commit id
                if (foundAFileSimilarToCommitId) {
                    System.out.println("No commit with that id exists.");
                    System.exit(0);
                } else {
                    foundAFileSimilarToCommitId = true;
                    completedSha1 = filename;
                }
            }
        }

        return completedSha1;
    }

    private static String getCommitSha1(Commit commit) {
        return serializeCommit(commit);
    }

}
