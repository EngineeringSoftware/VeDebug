package vedebug.core;

import java.io.File;

/**
 * Constants and configuration flags.
 *
 * @author Ben Buhse <bwbuhse@utexas.edu>
 * @author Thomas Wei <thomasw219@gmail.com>
 * @author Zhiqiang Zang <capapoc@gmail.com>
 */
public final class Names {

    /**
     * Configuration.
     */

    // If this is true, then the object graphs will be traversed; set
    // by sending -PagentArgs=t to the gradlew script.
    public static boolean traverse = false;

    public static String pkg = null;

    // Eventually there will be input so the file name can be entered at
    // runtime.
    public static final File PARENT = new File(System.getProperty("user.dir") + "/.vedebug");
    public static final File METHOD_ID_FILE = new File(PARENT, "MethodIDs.txt");
    public static final File METHOD_CALL_FILE = new File(PARENT, "MethodCalls.txt");
    public static final File TRAVERSAL_FILE = new File(PARENT, "ObjectGraphTraversals");

    // Indentation used for denoting object hierarchies
    public static final String SINGLE_INDENTATION = "  ";
    public static final String DOUBLE_INDENTATION = SINGLE_INDENTATION + SINGLE_INDENTATION;

    // This is basically the max length of calls before stuff is
    // printed to the file to avoid causing a stack overflow.  100,000
    // seems to be the fastest number to keep it at though 75,000 was
    // close, 150,000 was much slower.
    public static final int MAX_SIZE = 100000;

    /**
     * Classes
     */
    public static final String SAVE_UTIL_INTERNAL = "vedebug/core/SaveUtil";

    /**
     * Methods
     */
    public static final String SAVE_METHOD = "save";
}
