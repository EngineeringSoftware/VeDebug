package vedebug.core;

import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * An entry point to initiate the tracing tool.
 *
 * @author Ben Buhse <bwbuhse@utexas.edu>
 * @author Thomas Wei <thomasw219@gmail.com>
 * @author Zhiqiang Zang <capapoc@gmail.com>
 */
public class VideoAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        vedebugInit(agentArgs, inst);
    }

    private static void vedebugInit(String agentArgs, Instrumentation inst) {
        System.out.println("Vedebug Started");

        Path pathToInstrument = null;
        String packageToInstrument = null;
        String[] fileExtensions = null;

        // Parse agent arguments
        if (agentArgs != null) {
            String[] args = agentArgs.split("-");

            for (String arg : args) {
                if (arg.startsWith("t") || arg.startsWith("-traverse")) {
                    Names.traverse = !Names.traverse;
                } else if (arg.startsWith("p") || arg.startsWith("-path")) {
                    // Path only works for one path, so can't do a main and a test path right now
                    if (arg.matches("(p|-path) ?=.+")) {
                        pathToInstrument = Paths.get(arg.substring(arg.indexOf("=") + 1).trim());

                        if (!pathToInstrument.isAbsolute()) {
                            pathToInstrument = pathToInstrument.toAbsolutePath();
                        }
                    } else {
                        System.err.println("You must include the path with an = when using -p or --path." +
                                "\nUsing default settings instead.");
                    }
                } else if (arg.startsWith("P") || arg.startsWith("-package")) {
                    // If you give it a package then it will transform any classes in the given package or any sub-packages
                    if (arg.matches("(P|-package) ?=.+")) {
                        packageToInstrument = arg.substring(arg.indexOf("=") + 1).trim();
                    } else {
                        System.err.println("You must include the package with an = when using -P or --package." +
                                "\nUsing default settings instead.");
                    }
                } else if (arg.startsWith("f")) {
                    // If you give it file extensions to search, it will search for all of these (but also only these)
                    // This is needed for debugging JVM languages other than Java
                    if (arg.matches("f (\\w+ ?)+")) {
                        fileExtensions = arg.substring(1).trim().split(" ");
                    } else {
                        System.err.println("You must type the list of extensions to search with spaces" +
                                " in-between each file type and no period to start the extension." +
                                "\nUsing default settings instead.");
                    }
                }
            }
        }

        System.out.println("Object graph traversal: " + Names.traverse);
        System.out.println("Path(s) to instrument: " +
                (pathToInstrument == null && packageToInstrument == null ? "default" : "set"));
        System.out.println("File extensions to search: " + (fileExtensions == null ? "default" : "set"));

        if ((pathToInstrument != null) && (packageToInstrument != null)) {
            System.err.println("[WARNING] Setting both a package and path to transform is redundant as the path will be ignored.");
        }

        System.out.println("\n---------------------------------------\n");
        // insert the shutdown hook to save the trace
        SaveUtil.hook();
        inst.addTransformer(new VideoTransformer(pathToInstrument, packageToInstrument, fileExtensions));
    }
}
