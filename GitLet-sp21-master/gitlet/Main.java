package gitlet;

import java.io.File;

import static gitlet.Utils.join;

/**
 * Driver class for Gitlet, a subset of the Git version-control system.
 *
 * @author Linde
 */
public class Main {
    /**
     * The current working directory.
     */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /**
     * The .gitlet directory.
     */
    public static final File GITLET_DIR = join(CWD, ".gitlet");

    /**
     * If the args.length is less or more than function's need,
     * quit the entire program.
     * for example:
     * <p>
     * for init(),
     * the args should be ["init"], i.e. args.length should be 1
     * <p>
     * for branch(String branchName)
     * the args should be ["branch","someBranchName"],
     * i.e. args.length should be 2
     *
     * @param args   arguments passed to the function.
     * @param length the length of the arguments that function should take
     */
    private static void checkFuncArgumentLength(String[] args, int length) {
        // the first element in args is the function name
        if (args.length > length) {
            System.out.println("Incorrect operands.");
            System.exit(0);
        }
    }


    /**
     * Usage: java gitlet.Main ARGS, where ARGS contains
     * <COMMAND> <OPERAND1> <OPERAND2> ...
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please enter a command.");
            System.exit(0);
        }

        String firstArg = args[0];
        switch (firstArg) {
            case "init":
                checkFuncArgumentLength(args, 1);
                Repository.init();
                break;
            case "add":
                checkInitialize();
                checkFuncArgumentLength(args, 2);
                String filenameForAdd = args[1];
                Repository.add(filenameForAdd);
                break;
            case "commit":
                checkInitialize();
                checkFuncArgumentLength(args, 2);
                String message = args[1];
                Repository.setUpCommit(message);
                break;
            case "rm":
                checkInitialize();
                checkFuncArgumentLength(args, 2);
                String filenameForRemove = args[1];
                Repository.remove(filenameForRemove);
                break;
            case "log":
                checkInitialize();
                checkFuncArgumentLength(args, 1);
                Repository.log();
                break;
            case "global-log":
                checkInitialize();
                checkFuncArgumentLength(args, 1);
                Repository.globalLog();
                break;
            case "find":
                checkInitialize();
                checkFuncArgumentLength(args, 2);
                String messageToBeSearch = args[1];
                Repository.find(messageToBeSearch);
                break;
            case "checkout":
                checkInitialize();
                if (args.length == 3 && args[1].equals("--")) {
                    String filename = args[2];
                    Repository.checkoutFilename(filename);
                } else if (args.length == 4 && args[2].equals("--")) {
                    String commitId = args[1];
                    String filename = args[3];
                    Repository.checkoutCommitAndFilename(commitId, filename);
                } else if (args.length == 2) {
                    String branchName = args[1];
                    Repository.checkoutBranchName(branchName);
                } else {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }
                break;
            case "branch":
                checkInitialize();
                checkFuncArgumentLength(args, 2);
                String branchToBeCreatedName = args[1];
                Repository.branch(branchToBeCreatedName);
                break;
            case "rm-branch":
                checkInitialize();
                checkFuncArgumentLength(args, 2);
                String branchToBeRemovedName = args[1];
                Repository.removeBranch(branchToBeRemovedName);
                break;
            case "status":
                checkInitialize();
                checkFuncArgumentLength(args, 1);
                Repository.status();
                break;
            case "reset":
                checkInitialize();
                checkFuncArgumentLength(args, 2);
                String commitId = args[1];
                Repository.resetWithUncompletedCommitId(commitId);
                break;
            case "merge":
                checkInitialize();
                checkFuncArgumentLength(args, 2);
                String targetBranchName = args[1];
                Repository.merge(targetBranchName);
                break;
            default:
                System.out.println("No command with that name exists.");
                break;
        }

    }

    private static void checkInitialize() {
        if (!GITLET_DIR.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        }
    }


}
