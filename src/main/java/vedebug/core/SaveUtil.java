package vedebug.core;

import vedebug.util.Types;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Util class to collect and then store data to a file.
 * <p>
 * Most of these methods are never explicitly called in source code
 * but are instead made to be called using ASM.
 *
 * @author Ben Buhse <bwbuhse@utexas.edu>
 * @author Thomas Wei <thomasw219@gmail.com>
 * @author Zhiqiang Zang <capapoc@gmail.com>
 */
public class SaveUtil {

    private static int ARRAY_ITEMS = 5;

    // Doesn't add a newline to the start of the first line in MethodCalls.txt
    private static boolean firstLine = true;
    // Switches to append mode after print() is called once
    private static boolean firstPrint = true;

    // Variables used when traversing object graphs
    // These need to remain unchanged through method calls
    private static int traversalNum = 1;
    private static boolean firstObject = true;
    // List of traversals to save, basically the traversal version of the
    // calls list
    private static final List<String> traversals = new ArrayList<>();

    // These ones are used for traversing, too, but need to be reset at the
    // start of certain methods in the object graph traversal
    private static int objectIDCounter = 1;
    // The stack of objects that still need to be traversed
    private static final Queue<Object> toTraverse = new ArrayDeque<>();
    // The map of objects and their object ids, needed for linking circular
    // references
    private static final Map<Object, Integer> objectIDs = new HashMap<>();
    // The map of classes and their fields, this is used to make the program
    // slightly more efficient
    // It's very slight and this might not even be worth it...
    private static final Map<Class<?>, List<Field>> classFields = new HashMap<>();
    // The set of all the objects which have been visited already in the loop
    private static final Set<Object> visited = new HashSet<>();
    // Prevents stack overflows during object graph traversal
    private static boolean inTraversal;

    // ArrayList of strings which eventually is saved to the file when
    // the shutdown hook is called.
    private static final List<String> calls = new ArrayList<>();
    // Current call is appended to this, once a newline is reached it
    // gets added to the calls() list
    private static final StringBuilder currentCall = new StringBuilder();

    // Stores the IDs for each method
    // Only get printed to their file once since they're never changed during
    // runtime
    private static final Map<Integer, String> ids = new TreeMap<>();
    // Used to keep track of the line number each method starts at
    private static final Map<Integer, Integer> firstLineNums = new HashMap<>();
    // Used to keep track of the line number each method ends at
    private static final Map<Integer, Integer> lastLineNums = new HashMap<>();

    // Used so that BB files don't get truncated midway through
    private static final Set<String> visitedBBFiles = new HashSet<>();

    // This is used mainly for formatting, otherwise a simple method
    // call in VMV would have sufficed
    private static int currentInvocationLine = 0;

    // This is used to temporarily save the line number from which a
    // static method is called (the value of previous
    // currentInvocationLine). A call to the static initializing block
    // happens between saving the invocation line number of a static
    // method and a real call to the static method. This interrupts
    // the normal call of the static method and overrides the value of
    // currentInvocationLine. If we don't keep the value, we will lose
    // it and thus get a wrong invocation line number when we go back
    // from static initializing block and enter the static method.
    private static int currentInvocationLineBackup = 0;

    // Only need to be saved in this class because this Set is needed
    // in testing
    private static Set<Integer> allBasicBlocks;

    public static Map<Integer, String> getIds() {
        return ids;
    }

    public static List<String> getCalls() {
        // This needs to be done because otherwise the closing line
        // for main won't be inserted
        flushCurrentCall();
        return calls;
    }

    public static Set<Integer> getBasicBlockLineNums() {
        return allBasicBlocks;
    }

    /**
     * Returns map of (method id, last line number).
     *
     * @return map of (method id, last line number).
     */
    public static Map<Integer, Integer> getLastLineNums() {
        return lastLineNums;
    }

    public static void setActualLastLineNum(int id) {
        int maxBB = allBasicBlocks.isEmpty() ? 0 : Collections.max(allBasicBlocks);

        try {
            if (lastLineNums.get(id) < maxBB) {
                lastLineNums.put(id, maxBB);
            }
        } catch (NullPointerException e) {
            // TODO: Correctly handle NullPointerException caused by
            // methods without a line number table, since putting (id,
            // 0) into the map would not solve this because it still
            // crashes in the Python script.
        }
    }

    // Used to keep the current invocation line number
    // When this is set to a value != 0 it will be used the next time that
    // save(String) is called
    @SuppressWarnings("unused")
    public static void setCurrentInvocationLine(int currentInvocationLine) {
        SaveUtil.currentInvocationLine = currentInvocationLine;
    }

    public static void setPkg(String pkg) {
        Names.pkg = pkg;
    }

    // Used in testing to clear all the important variables before
    // every test.
    public static void clear() {
        firstLine = true;
        firstPrint = true;

        calls.clear();
        ids.clear();
        firstLineNums.clear();
        lastLineNums.clear();
        currentCall.setLength(0);
        traversals.clear();

        currentInvocationLine = 0;
        VideoMethodVisitor.resetIDCounter();
    }

    // Called in the Method Visitor to save method calls and returns.
    // Used through ASM.
    @SuppressWarnings("unused")
    public static void save(String str) {
        // Labels with line zero exist so the basic block collection
        // thinks they should be saved, this ignores those.
        if (str.startsWith("@") || str.startsWith("-")) {
            // Solves issues with implicit method calls right before a basic
            // block change
            // that would cause a line number to be placed before the basic
            // block
            currentInvocationLine = 0;

            // If it's a "basic block" at line 0 it can be skipped
            // since it doesn't actually exist
            if (str.charAt(1) == '0') {
                return;
            }
        }

        // If this is the very first method being called, it should
        // not start with a newline, otherwise it should
        if (firstLine) {
            firstLine = false;
        } else {
            appendCurrentCall("\n");
        }

        // The else-if branch of this may be broken ..?
        if (currentInvocationLine != 0) {
            appendCurrentCall(currentInvocationLine + " ");
            currentInvocationLine = 0;
        } else if (VideoMethodVisitor.isSavingInvocationLineNums && Character.isDigit(str.charAt(0))) {
            appendCurrentCall(getInvocationLineNumNotFromStackWalker(false) + " ");
        }

        appendCurrentCall(str + " ");
    }

    // The rest of the save() methods are for saving actual argument
    // and returns values to the current call; There's one for every
    // primitive (aside from byte and short which use the int one)
    // plus for objects.

    @SuppressWarnings("unused")
    public static void save(int i) {
        appendCurrentCall(i + " ");
    }

    @SuppressWarnings("unused")
    public static void save(long l) {
        appendCurrentCall(l + " ");
    }

    @SuppressWarnings("unused")
    public static void save(char c) {
        appendCurrentCall(c + " ");
    }

    @SuppressWarnings("unused")
    public static void save(float f) {
        appendCurrentCall(f + " ");
    }

    @SuppressWarnings("unused")
    public static void save(double d) {
        appendCurrentCall(d + " ");
    }

    @SuppressWarnings("unused")
    public static void save(boolean b) {
        appendCurrentCall(b + " ");
    }

    @SuppressWarnings("unused")
    public static void save(Object o) {
        if (o != null) {
            // If it's a String then even though it's an object we can
            // just appendCurrentCall it to the call StringBuilder on
            // its own.
            if (o instanceof String) {
                appendCurrentCall("\"" + ((String) o).replace("\n", "\\n") + "\" ");
            } else if (o.getClass().isArray()) {
                // If the object is an array, then don't actually go
                // through it; use this util method instead.
                saveArray(o);
            } else if (Names.traverse) {
                if (Names.pkg != null && o.getClass().getPackage().getName().startsWith(Names.pkg)) {
                    traverseObjectGraph(o);
                } else if (!Types.isIgnoredClassName(o.getClass().getName().replaceAll("\\.", "/"))) {
                    traverseObjectGraph(o);
                }
            }
        } else {
            appendCurrentCall("null ");
        }
    }

    // This is only used in methods named <clinit> to capture the line
    // number where they were called.  It's used so that the python
    // script knows when to go to the location of clinit in the code.
    @SuppressWarnings("unused")
    public static void saveInvocationLineNumber() {
        // Get the invocation line number of clinit from stack
        currentInvocationLine = getInvocationLineNumNotFromStackWalker(true);
    }

    // Backup the invocation line number of the previous called static method
    public static void backupInvocationLineNumber() {
        currentInvocationLineBackup = currentInvocationLine;
    }

    // Recover the invocation line number of the previous called static method
    public static void recoverInvocationLineNumber() {
        currentInvocationLine = currentInvocationLineBackup;
    }

    // Downgrade to Java 8. This is refactored method of
    // getInvocationLineNumFromStackWalker, but without using
    // StackWalker(since Java 9).
    private static int getInvocationLineNumNotFromStackWalker(boolean clinit) {
        // Uses class StackTraceElement to get the line number where <clinit>
        // was invoked.

        List<StackTraceElement> preStackTrace =
                new ArrayList<StackTraceElement>(Arrays.asList(Thread.currentThread().getStackTrace()));
        List<StackTraceElement> stackTrace =
                new ArrayList<StackTraceElement>();
        for (int j = 0; j < preStackTrace.size(); j++) {
            if (checkClassIsInPkg(preStackTrace.get(j).getClassName())) {
                stackTrace.add(preStackTrace.get(j));
            }
        }

        /* i has to be declared outside of the loop because it's used once
         * again after the loop ends
         */
        int i;
        /* The magicNumber is based on the number of other methods (outside
         * of those being instrumented) which are called while getting the
         * stackTrace. The number is used so that those methods are ignored
         * when collecting the invocation line number. It evidently varies
         * based on the Java version being used and the method that is being
         * used to get the stackTrace (i.e. StackWalker vs StackTraceElements)
         */
        int magicNumber = 2;

        if (stackTrace.size() <= magicNumber) {
            return stackTrace.get(stackTrace.size() - 1).getLineNumber();
        }

        for (i = magicNumber; i < stackTrace.size(); i++) {

            if (checkClassIsInPkg(stackTrace.get(i).getClassName())) {
                break;
            }

            if (i == stackTrace.size() - 1) {
                return -1;
            }
        }

        return stackTrace.get(clinit ? i - 1 : i).getLineNumber();
    }

    // Shutdown hook used to save whatever is left in calls after the
    // program ends; used through ASM.
    @SuppressWarnings("unused")
    public static void hook() {
        Runtime.getRuntime().addShutdownHook(new Thread(SaveUtil::onShutdown));
    }

    // Adds a method id and its starting line number to the lineNum
    // HashMap.
    public static void insertLineNum(int id, int lineNum) {
        firstLineNums.put(id, lineNum);
    }

    // Replace a line number, which is used when handling constructors
    // that include super() or this().
    public static void replaceLineNum(int id, int lineNum) {
        firstLineNums.replace(id, lineNum);
    }

    // Adds a method id and its ending line number to the lastLineNum
    // HashMap.
    public static void insertLastLineNum(int id, int lineNum) {
        lastLineNums.put(id, lineNum);
    }

    // Adds the method ids and their respective "method strings" to
    // the ids map.
    public static void insertMethodID(int id, String methodString) {
        ids.put(id, methodString);
    }

    // Wipes and then creates a new file for saving the basic blocks
    // for each .java file.
    public static void createBasicBlockFile(String fullSourceSplitByDash) {
        //noinspection ResultOfMethodCallIgnored
        Names.PARENT.mkdirs();

        if (!visitedBBFiles.contains(fullSourceSplitByDash)) {
            visitedBBFiles.add(fullSourceSplitByDash);

            try (FileWriter fw = new FileWriter(new File(Names.PARENT, fullSourceSplitByDash + "BB"));
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter out = new PrintWriter(bw)) {
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // This is called in VMV's visitEnd() method.  Saves all the BB
    // line numbers for each method instrumented.
    public static void printBasicBlocksToFile(String fullSourceSplitByDash, Set<Integer> basicBlockLineNums) {
        // This prevents duplicate line numbers from being written to the BB file by reading in all previously written nums
        Path pathToBBFile = Paths.get(Names.PARENT + "/" + fullSourceSplitByDash + "BB");
        if (Files.exists(pathToBBFile)) {
            // Reads all the lines already in the file and then puts them in a List<Integer>
            Set<Integer> oldNums = null;
            try {
                oldNums = Files.lines(pathToBBFile)
                        .map(String::trim)
                        .mapToInt(Integer::parseInt)
                        .boxed()
                        .collect(Collectors.toSet());
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (oldNums != null) {
                basicBlockLineNums.addAll(oldNums);
            }

        }

        SaveUtil.allBasicBlocks = basicBlockLineNums;

        try (FileWriter fw = new FileWriter(new File(Names.PARENT, fullSourceSplitByDash + "BB"), false);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            basicBlockLineNums.forEach(out::println);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Sorts the basic blocks for a source file at the end of every
    // class.  This is really only necessary when there are nested
    // classes, otherwise it doesn't actually change anything.
    public static void sortBasicBlocks(String classNameSplitByDash) {
        Path path = Paths.get(Names.PARENT + "/" + classNameSplitByDash + "BB");

        List<Integer> basicBlocks = new ArrayList<>();
        try {
            basicBlocks =
                    Files.lines(path).map(Integer::parseInt).sorted().collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (FileWriter fw = new FileWriter(new File(Names.PARENT, classNameSplitByDash + "BB"));
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            basicBlocks.forEach(out::println);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Used to ignore certain classes
    private static boolean checkClassIsInPkg(String className) {
        className = className.replace(".", "/");
        if (Names.pkg != null && !className.startsWith(Names.pkg)) {
            return false;
        }
        return Names.pkg != null || !Types.isIgnoredClassName(className);
    }

    // Used as a util method on-top of save(Object) when the object is
    // an array; for arrays we only get the first N items in them.
    private static void saveArray(Object o) {
        if (o.getClass().getComponentType().isPrimitive()) {
            // If it's primitive it needs to be cast to the exact
            // right type.
            savePrimitiveArray(o);
        } else if (o instanceof String) {
            // if it's an array of String, we record the first N
            // elements of the array.
            Object[] oAsArray = (Object[]) o;
            StringBuilder arrAsString = new StringBuilder("[");

            for (int i = 0; i < ARRAY_ITEMS && i < oAsArray.length; i++) {
                arrAsString.append(", ").append(oAsArray[i]);
            }
            if (oAsArray.length > ARRAY_ITEMS) {
                arrAsString.append(", ...");
            }
            appendCurrentCall(arrAsString.append("]").toString().replaceFirst(", ", ""));
        } else {
            // Otherwise we can use our methods that are designed for
            // the Object type.
            Object[] oAsArray = (Object[]) o;
            StringBuilder arrAsString = new StringBuilder("[");

            for (int i = 0; i < ARRAY_ITEMS && i < oAsArray.length; i++) {
                // [ATTENTION] We cannot implicitly call toString() of
                // oAsArray[i] except it is a String itself, which is
                // the root cause of the problem of agentdemo. I
                // don't know what we can do except to print
                // toString(). For now just print the class name.
                arrAsString.append(", ").append(o.getClass().getComponentType().toString().split(" ")[1]);
                // arrAsString.append(", ").append(oAsArray[i]);
            }

            if (oAsArray.length > ARRAY_ITEMS) {
                arrAsString.append(", ...");
            }
            appendCurrentCall(arrAsString.append("]").toString().replaceFirst(", ", ""));
        }
        appendCurrentCall(" ");
    }

    // Used even more on top of saveArray() for saving primitive
    // arrays specifically.
    private static void savePrimitiveArray(Object o) {
        // This will never be null because the only time this method
        // should be called is after already determining that the
        // object is an array.
        Class<?> clz = o.getClass().getComponentType();
        StringBuilder arrAsString = new StringBuilder("[");
        int length = -1;

        // This is pretty gross because of Java typing making it
        // complicated to deal with an array where you're not sure
        // what primitive type it is.  Basically, if the array is N or
        // less elements long it just prints its toString otherwise it
        // prints its toString with a ... as the (N+1)th element.
        if (clz.equals(byte.class)) {
            byte[] oAsArray = (byte[]) o;
            length = oAsArray.length;
            for (int i = 0; i < ARRAY_ITEMS && i < oAsArray.length; i++) {
                arrAsString.append(", ").append(oAsArray[i]);
            }
        } else if (clz.equals(char.class)) {
            char[] oAsArray = (char[]) o;
            length = oAsArray.length;
            for (int i = 0; i < ARRAY_ITEMS && i < oAsArray.length; i++) {
                if (oAsArray[i] == '\n') {
                    arrAsString.append(", ").append("\\n");
                } else {
                    arrAsString.append(", ").append(oAsArray[i]);
                }
            }
        } else if (clz.equals(short.class)) {
            short[] oAsArray = (short[]) o;
            length = oAsArray.length;
            for (int i = 0; i < ARRAY_ITEMS && i < oAsArray.length; i++) {
                arrAsString.append(", ").append(oAsArray[i]);
            }
        } else if (clz.equals(int.class)) {
            int[] oAsArray = (int[]) o;
            length = oAsArray.length;
            for (int i = 0; i < ARRAY_ITEMS && i < oAsArray.length; i++) {
                arrAsString.append(", ").append(oAsArray[i]);
            }
        } else if (clz.equals(long.class)) {
            long[] oAsArray = (long[]) o;
            length = oAsArray.length;
            for (int i = 0; i < ARRAY_ITEMS && i < oAsArray.length; i++) {
                arrAsString.append(", ").append(oAsArray[i]);
            }
        } else if (clz.equals(float.class)) {
            float[] oAsArray = (float[]) o;
            length = oAsArray.length;
            for (int i = 0; i < ARRAY_ITEMS && i < oAsArray.length; i++) {
                arrAsString.append(", ").append(oAsArray[i]);
            }
        } else if (clz.equals(double.class)) {
            double[] oAsArray = (double[]) o;
            length = oAsArray.length;
            for (int i = 0; i < ARRAY_ITEMS && i < oAsArray.length; i++) {
                arrAsString.append(", ").append(oAsArray[i]);
            }
        } else if (clz.equals(boolean.class)) {
            boolean[] oAsArray = (boolean[]) o;
            length = oAsArray.length;
            for (int i = 0; i < ARRAY_ITEMS && i < oAsArray.length; i++) {
                arrAsString.append(", ").append(oAsArray[i]);
            }
        }

        if (length > ARRAY_ITEMS) {
            arrAsString.append(", ...");
        }

        appendCurrentCall(arrAsString.append("]").toString().replaceFirst(", ", ""));
    }

    @SuppressWarnings("unused")
    @Deprecated
    private static String traverseObjectGraphRecursively(Object object) {
        StringBuilder str = new StringBuilder();
        // This is the what eventually gets appended to the calls list
        traversalHelper(object, str, new HashSet<>());
        return str.toString().replace(", }", "}");
    }

    @Deprecated
    private static void traversalHelper(Object object, StringBuilder str,
                                        Set<Object> visited) {
        // If the object is null, you can obviously skip it
        if (object == null) {
            str.append("null");
            return;
        }

        // Starts with the object's class's name and a curly brace
        str.append(object.getClass().getName()).append("{");

        // If this object has already been visited (because of a circular
        // reference) then add a symbol to signify that and leave
        if (visited.contains(object)) {
            str.append("<-").append("}");
            return;
        }
        visited.add(object);

        Class<?> clz = object.getClass();

        // Get the list of fields in the object's class in a sorted order so
        // that the output is consistent between runs
        List<Field> fields = Stream.of(clz.getDeclaredFields())
                .sorted(Comparator.comparing(Field::getName))
                .collect(Collectors.toList());

        try {
            // For each field, either add its value (if the field is
            // primitive) or recursively call this method
            for (Field field : fields) {
                // Don't need to keep track of this so skip back to top of
                // for-each loop
                if (Modifier.isStatic(field.getModifiers()) ||
                        field.isSynthetic()) {
                    continue;
                }

                field.setAccessible(true);

                if (field.getType().isPrimitive() || field.getType().getName().endsWith("String")) {
                    str.append(field.get(object));
                } else {
                    traversalHelper(field.get(object), str, visited);
                }

                str.append(", ");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        str.append("}");
    }

    // Traverse the object graph with recursion.  This is the
    // preferred method to use (with a different output format than
    // the recursive method).
    private static void traverseObjectGraph(Object object) {
        if (inTraversal) {
            return;
        }
        // If this is the first object whose graph is being traversed, the
        // file needs to be created
        if (firstObject) {
            firstObject = false;
            createObjectGraphTraversalFile();
        }

        // Append the traversal number to the calls str so it can be linked
        // to the traversal file
        appendCurrentCall("T#" + traversalNum + " ");

        // If the object is null, don't bother going into the other method
        if (object == null) {
            traversals.add("T#" + traversalNum++ + "O#1\nnull");
            return;
        }

        inTraversal = true;
        traverseObjectGraphNotNull(object);
        inTraversal = false;
    }

    // The rest of the traversal which happens when objects are not
    // nulls.
    private static void traverseObjectGraphNotNull(Object object) {
        checkSizes();

        // Reset all the variables for this run-through of the method
        objectIDCounter = 1;
        toTraverse.clear();
        objectIDs.clear();
        visited.clear();

        // The string which eventually gets saved to the traversal file
        StringBuilder str = new StringBuilder("T#" + traversalNum++);

        // Add the initial object to the stack and set its ID
        toTraverse.add(object);
        objectIDs.put(object, objectIDCounter++);

        int count = 0;

        // While there are still objects to be traversed, keep looping
        // through here
        while (!toTraverse.isEmpty()) {
            count++; // Add 1 to the count of objects that have been traversed

            checkSizes(); // Make sure the variables on the heap haven't
            // grown too big, if they have then print them

            // The object to be traversed for this iteration through the loop
            Object current = toTraverse.poll();

            // If this object is part of Java or has already been visited
            // then skip it
            if (current == null ||
                    current.getClass().getPackage().getName().startsWith("java") ||
                    current.getClass().getPackage().getName().startsWith("jdk") ||
                    current.getClass().getPackage().getName().startsWith("com.google") ||
                    // If the object has already been visited, skip to the
                    // next object in the stack
                    visited.contains(current)) {
                continue;
            }
            // Add the object to visited so this method own't get here on it
            // again
            visited.add(current);

            // Add the object id and type to str
            //noinspection ConstantConditions
            str.append("\n").append(Names.SINGLE_INDENTATION).append("O#").append(objectIDs.get(current)).append(" - ").append(current.getClass().getName());

            // Get the object's fields in alphabetical order by name
            // If the same type of object has already been visited, it just
            // needs to get a reference to the list from the map
            List<Field> fields = classFields.get(current.getClass());
            if (fields == null) {
                fields = Stream.of(current.getClass().getDeclaredFields())
                        .sorted(Comparator.comparing(Field::getName))
                        .filter(field -> !Modifier.isStatic(field.getModifiers()) && !field.isSynthetic())
                        .collect(Collectors.toList());

                classFields.put(current.getClass(), fields);
            }

            // Traverse any fields which the object may have
            traverseFields(current, fields, str);

            // If this is the 5th object to be traversed, break from the loop
            if (count >= ARRAY_ITEMS) {
                str.append("...");
                break;
            }
        }

        traversals.add(str.toString());
    }

    private static void traverseFields(Object current, List<Field> fields, StringBuilder str) {
        try {
            // Same purpose as the count in traverseObjectGraphNotNull but
            // for the number of fields in each object
            int count = 0;

            // Do this for each (well the first five) fields in the list of
            // fields for current's type
            for (Field field : fields) {
                count++;

                // Make the field accessible so that reflection can be done
                // on it
                field.setAccessible(true);

                if (field.getType().isPrimitive() || field.getType().getName().endsWith("String")) {
                    // If the field is primitive or a String, we can just add
                    // it to str
                    str.append("\n    ").append(field.get(current));
                } else {
                    // Otherwise it's an object and we must add it to the
                    // stack of objects left to traverse
                    Object temp = field.get(current);
                    int tempID;

                    // If temp has already been visited, then just get its id
                    // that has already been established
                    // Else give it an ID and use that
                    if (visited.contains(temp)) {
                        tempID = objectIDs.get(temp);
                    } else {
                        tempID = objectIDCounter++;
                    }

                    // These needs to get added even for null objects
                    str.append("\n").append(Names.DOUBLE_INDENTATION).append("O#").append(tempID).append(" - ");
                    // If null, add that, otherwise add the class's name
                    if (temp != null) {
                        str.append(temp.getClass().getName());
                        toTraverse.add(temp);
                        objectIDs.put(temp, tempID);
                    } else {
                        str.append("null");
                    }
                }

                // If this is the Nth field visited for this object then
                // break from the loop
                if (count >= ARRAY_ITEMS) {
                    str.append("...");
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Appends messages to the end of the currentCall StringBuilder.
    // When the message is a '\n', the currentCall is flushed
    private static void appendCurrentCall(String msg) {
        checkSizes();
        // this looks sorta weird but it will still work if msg is null for
        // whatever reason
        if ("\n".equals(msg)) {
            currentCall.append(msg);
            flushCurrentCall();
        } else {
            currentCall.append(msg);
        }
    }

    // Adds the currentCall StringBuilder to the calls list and then
    // clears the currentCall StringBuilder.
    private static void flushCurrentCall() {
        calls.add(currentCall.toString());
        currentCall.setLength(0);
    }

    // Once calls and traversals combined have more than MAX_SIZE
    // elements, the files are all updated to clear up heap space.
    private static void checkSizes() {
        if (calls.size() + traversals.size() > Names.MAX_SIZE) {
            print();
            printTraversalsToFile();
            calls.clear();
            ids.clear();
            traversals.clear();
        }
    }

    private static void createObjectGraphTraversalFile() {
        //noinspection ResultOfMethodCallIgnored
        Names.PARENT.mkdirs();
        try (FileWriter fw = new FileWriter(new File(Names.PARENT, "ObjectGraphTraversals"));
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Saves a single traversal string to the ObjectGraphTraversals
    // file.
    private static void printTraversalsToFile() {
        try (FileWriter fw = new FileWriter(Names.TRAVERSAL_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            traversals.forEach(out::println);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Prints all the various information that is collected to its
    // respective file.
    private static void print() {
        flushCurrentCall();

        // First print the method ids
        try (FileWriter fw = new FileWriter(Names.METHOD_ID_FILE, !firstPrint);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            // TODO: Find a better approach to fix Python script crash
            // due to "null" in MethodIDs.txt ( -1 here avoids crash
            // but do nothing meaningful)
            ids.forEach((k, v) -> out.println(k + " "
                    + (firstLineNums.get(k) == null ? -1 : firstLineNums.get(k)) + " "
                    + (lastLineNums.get(k) == null ? -1 : lastLineNums.get(k)) + " "
                    + v));
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Then print the method calls
        try (FileWriter fw = new FileWriter(Names.METHOD_CALL_FILE, !firstPrint);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            calls.forEach(out::print);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        firstPrint = false;
    }

    // Used to simplify the shutdown hook
    private static void onShutdown() {
        printTraversalsToFile();
        print();
    }
}
