package vedebug.util;

import java.util.Arrays;
import java.util.List;

/**
 * Util class for checking type related info.
 *
 * @author Ben Buhse <bwbuhse@utexas.edu>
 * @author Thomas Wei <thomasw219@gmail.com>
 * @author Zhiqiang Zang <capapoc@gmail.com>
 */
public class Types {

    /** A blacklist of packages in which we do not want to instrument classes */
    private static final List<String> BLACKLIST = Arrays.asList("com/intellij/",
                                                                "com/sun/",
                                                                "java/",
                                                                "javax/",
                                                                "jdk/",
                                                                "junit/",
                                                                "org/apache/maven/",
                                                                "org/graalvm/",
                                                                "org/groovy/",
                                                                "org/hamcrest/",
                                                                "org/intellij/",
                                                                "org/jetbrains/",
                                                                "org/junit/",
                                                                "scala/",
                                                                "sun/",
                                                                "vedebug/"
                                                                );

    // For vedebug/test/VideoTransformerTest to work
    private static final List<String> WHITELIST = Arrays.asList("vedebug/test/");

    /** Decide whether or not a class name should be ignored   */
    public static boolean isIgnoredClassName(String className) {
        for (String s : WHITELIST) {
            if (className.startsWith(s)) {
                return false;
            }
        }
        for (String s : BLACKLIST) {
            if (className.startsWith(s)) {
                return true;
            }
        }
        return false;
    }
}
