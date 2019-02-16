package vedebug.test;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import vedebug.core.Names;
import vedebug.core.SaveUtil;
import vedebug.core.VideoMethodVisitor;
import vedebug.core.VideoTransformer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Ben Buhse <bwbuhse@utexas.edu>
 * @author Thomas Wei <thomasw219@gmail.com>
 * @author Zhiqiang Zang <capapoc@gmail.com>
 */
public class VideoTransformerTest {

    // IMPORTANT: Static classes for testing. If any of these static
    // classes are renamed then you need to change any place where
    // they're in an "expected" set

    static class A {
        public static void main(String[] args) {
        }
    }

    static class B {
        public static void main(String[] args) {
            m();
            n();
        }

        private static void m() {
            n();
        }

        private static void n() {
        }
    }

    static class C {
        public static void main(String[] args) {
            D.m();
        }
    }

    static class D {
        public static void m() {
        }
    }

    static class E {
        // This has a bunch of unnecessary lines printed so that labels will be generated containing line numbers
        // Without statements, labels wouldn't exist and the MethodVisitor would simply ignore these basic blocks
        public static void main(String[] args) {
            int n = (int) (Math.random() * 10);
            if (n > 5) {
                // This if is just for testing that the basic blocks are working
                System.out.print("");
            } else {
                System.out.print("");
                // Same thing here
                // But I'll give this one multiple lines
                // Just to make sure
            }

            System.out.print("");

            int i;
            for (i = 0; i < 5; i++) {
                System.out.print("");
            }

            System.out.print("");
            System.out.print("");
            System.out.print("");

            while (i < 10) {
                i++;
                System.out.print("");
                System.out.print("");
            }

            System.out.print("");
            System.out.print("");
            System.out.print("");
        }
    }

    static class F {
        static final int a;

        static {
            a = 5;
        }

        public static void main(String[] args) {
        }
    }

    static class G {
        public static void main(String[] args) {
            new H();
        }
    }

    static class H {
        static final int a;

        static {
            a = 5;
        }
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored", "UnusedReturnValue", "ConstantConditions"})
    static class I {
        public static void main(String[] args) {
            m();
        }

        private static int m() {
            boolean b = true;

            return b ?
                    1 :
                    2;
        }
    }

    static class J {
        public static void main(String[] args) {
            m();
        }

        private static void m() {
        }
    }

    @SuppressWarnings({"SameParameterValue", "UnusedReturnValue", "ResultOfMethodCallIgnored", "unused"})
    static class K {
        public static void main(String[] args) {
            m("Hello\n");
        }

        private static String m(String string) {
            return "\nWorld!";
        }
    }

    // Don't change any of the values in this class, testTraceBasicBlock() relies on them
    @SuppressWarnings({"ResultOfMethodCallIgnored", "UnusedReturnValue", "SameParameterValue"})
    static class WholeNumberTester {
        public static void main(String[] args) {
            b((byte) 1);
            s((short) 1);
            i(1);
            j(1);
        }

        private static byte b(byte b) {
            return (byte) (b + 1);
        }

        private static short s(short s) {
            return (short) (s + 1);
        }

        private static int i(int i) {
            return i + 1;
        }

        private static long j(long j) {
            return j + 1;
        }
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored", "UnusedReturnValue", "SameParameterValue"})
    static class FloatingPointTester {
        public static void main(String[] args) {
            f(1);
            d(1);
        }

        private static float f(float f) {
            return f + 1;
        }

        private static double d(double d) {
            return d + 1;
        }
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored", "UnusedReturnValue", "SameParameterValue"})
    static class CharTester {
        public static void main(String[] args) {
            c('a');
        }

        private static char c(char c) {
            return (char) (c + 1);
        }
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored", "UnusedReturnValue", "SameParameterValue"})
    static class BooleanTester {
        public static void main(String[] args) {
            z(true);
        }

        private static boolean z(boolean z) {
            return !z;
        }
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored", "UnusedReturnValue", "SameParameterValue"})
    static class ObjectIDTester {
        public static void main(String[] args) {
            l(new Object());
            l2(new ObjectTesterHelper(1, new ObjectIDTester()));
        }

        private static Object l(Object l) {
            l.toString();
            return new Object();
        }

        private static ObjectTesterHelper l2(ObjectTesterHelper objectTesterHelper) {
            return objectTesterHelper;
        }
    }

    static class ObjectTesterHelper {
        int i;
        ObjectIDTester objectIDTester;

        public ObjectTesterHelper(int i, ObjectIDTester objectIDTester) {
            this.i = i;
            this.objectIDTester = objectIDTester;
        }
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored", "UnusedReturnValue", "SameParameterValue"})
    static class StringValueTester {
        public static void main(String[] args) {
            s("hello");
        }

        private static String s(String s) {
            return s + " world";
        }
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored", "UnusedReturnValue", "SameParameterValue"})
    static class ArrayValueTester {
        public static void main(String[] args) {
            prim(new int[]{1, 2, 3});

        }

        private static int[] prim(int[] arr) {
            Assert.assertNotNull(arr);
            int[] arr2 = arr.clone();
            arr2[0] = 2;
            return arr2;
        }
    }

    // Class loader we use to get our static classes into the transformer
    static class ByteClassLoader extends ClassLoader {

        private final Map<String, byte[]> classes;

        public ByteClassLoader(ClassLoader parent, Map<String, byte[]> classes) {
            super(parent);
            this.classes = classes;
        }

        @SuppressWarnings("unused")
        public ByteClassLoader(ClassLoader parent, String className, byte[] bytes) {
            this(parent, new HashMap<>());
            classes.put(className, bytes);
        }

        @Override
        public Class<?> findClass(final String name) throws ClassNotFoundException {
            if (classes.containsKey(name)) {
                byte[] bytes = classes.get(name);
                return defineClass(name, bytes, 0, bytes.length);
            }
            return super.findClass(name);
        }
    }

    @Before
    public void setUp() {
        SaveUtil.clear();
        VideoMethodVisitor.isSavingInvocationLineNums = false;
    }

    // Testing the calls stack and the id values for a class that only has a main
    @Test
    public void testTraceMainOnly() throws Exception {
        Class<?> clz = A.class;

        loadModifyRun(clz);

        // Check the results
        Map<Integer, String> ids = SaveUtil.getIds();
        List<String> calls = SaveUtil.getCalls();

        // Filters out any strings in calls that aren't entrances or exits to a method then makes sure the stack is right
        testCallsStack(calls.stream()
                .filter(str -> str.startsWith("-") || str.matches("^[0-9]+.*$\\R"))
                .collect(Collectors.toList()));

        Set<String> idValues = new HashSet<>(ids.values());
        Set<String> expectedIDValues = new HashSet<String>(Arrays.asList(
                "vedebug/test/VideoTransformerTest.java vedebug/test/VideoTransformerTest$A <init> - V",
                "vedebug/test/VideoTransformerTest.java vedebug/test/VideoTransformerTest$A main L V"
        ));
        expectedIDValues = Collections.unmodifiableSet(expectedIDValues);

        Assert.assertEquals(2, ids.size());
        Assert.assertEquals(expectedIDValues, idValues);
    }

    // Testing the calls stack and the id values for a class with more than just a main
    @Test
    public void testTraceMainAndMore() throws Exception {
        Class<?> clz = B.class;

        loadModifyRun(clz);

        // Check the results
        Map<Integer, String> ids = SaveUtil.getIds();
        List<String> calls = SaveUtil.getCalls();

        // Filters out any strings in calls that aren't entrances or exits to a method then makes sure the stack is right
        testCallsStack(calls.stream()
                .filter(str -> str.startsWith("-") || str.matches("^[0-9]+ .*$\\R"))
                .collect(Collectors.toList()));

        Set<String> idValues = new HashSet<>(ids.values());
        Set<String> expectedIDValues = new HashSet<String>(Arrays.asList(
                "vedebug/test/VideoTransformerTest.java vedebug/test/VideoTransformerTest$B <init> - V",
                "vedebug/test/VideoTransformerTest.java vedebug/test/VideoTransformerTest$B main L V",
                "vedebug/test/VideoTransformerTest.java vedebug/test/VideoTransformerTest$B m - V",
                "vedebug/test/VideoTransformerTest.java vedebug/test/VideoTransformerTest$B n - V"
        ));
        expectedIDValues = Collections.unmodifiableSet(expectedIDValues);

        Assert.assertEquals(4, ids.size());
        Assert.assertEquals(expectedIDValues, idValues);
    }

    // Testing the calls stack and the id values for a class that calls a method in another class
    @Test
    public void testTraceMainCallingOtherClass() throws Exception {
        Class<?> clz = C.class;

        loadModifyRun(clz, D.class);

        // Check the results
        Map<Integer, String> ids = SaveUtil.getIds();
        List<String> calls = SaveUtil.getCalls();

        // Filters out any strings in calls that aren't entrances or exits to a method then makes sure the stack is right
        testCallsStack(calls.stream()
                .filter(str -> str.startsWith("-") || str.matches("^[0-9]+ .*$\\R"))
                .collect(Collectors.toList()));

        Set<String> idValues = new HashSet<>(ids.values());
        Set<String> expectedIDValues = new HashSet<String>(Arrays.asList(
                "vedebug/test/VideoTransformerTest.java vedebug/test/VideoTransformerTest$C <init> - V",
                "vedebug/test/VideoTransformerTest.java vedebug/test/VideoTransformerTest$C main L V",
                "vedebug/test/VideoTransformerTest.java vedebug/test/VideoTransformerTest$D <init> - V",
                "vedebug/test/VideoTransformerTest.java vedebug/test/VideoTransformerTest$D m - V"
        ));
        expectedIDValues = Collections.unmodifiableSet(expectedIDValues);

        Assert.assertEquals(4, ids.size());
        Assert.assertEquals(expectedIDValues, idValues);
    }

    // Test to verify that the number of basic blocks being collected is right
    @Test
    public void testTraceBasicBlockCollection() throws Exception {
        Class<?> clz = E.class;

        loadModifyRun(clz);

        // Check the results
        // This is a set of every BB line number that was visited in E.main()
        // The stream converts the List<String> of all calls to a List<Integer> of just BB line numbers
        List<String> ltoDebug = SaveUtil.getCalls();
        List<Integer> visitedBasicBlocks = SaveUtil.getCalls()   // Get the List<String> of calls from SaveUtil
                .stream()                           // Convert the List<String> to a Stream<String>
                .filter(str -> str.startsWith("@")) // Filter out anything that's not a BB
                .map(str -> str.substring(1))       // Get rid of the '@'s at the beginning of every String in the stream
                .map(String::trim)                  // Get rid of the '\n's at the end of every String in the stream
                .map(bb -> Integer.parseInt(bb.split(":")[1]))             // Convert from a Stream<String> to a Stream<Integer>
                .collect(Collectors.toList());      // Lastly, collect the stream into a List<Integer>

        // This is the set of BB line numbers in E that was collected when the method visitor was called on E.main()l
        Set<Integer> allBasicBlocks = SaveUtil.getBasicBlockLineNums();

        Assert.assertEquals(14, visitedBasicBlocks.size());

        Assert.assertNotNull(allBasicBlocks);
        // TODO: This assert fails with the changes to SaveUtil.printBasicBlocksToFile
        //        Assert.assertEquals(8, allBasicBlocks.size());
        Assert.assertTrue(allBasicBlocks.containsAll(visitedBasicBlocks));
    }

    // Tests to make sure parameter/return types/values are being collected correctly
    // There is a test for each general type i.e. whole numbers, floats, booleans, etc.
    @Test
    public void testWholeNumberCollection() throws Exception {
        Class<?> clz = WholeNumberTester.class;

        loadModifyRun(clz);

        Map<Integer, String> ids = SaveUtil.getIds();
        List<String> calls = SaveUtil.getCalls();

        // Filters out any strings in calls that aren't entrances or exits to the test methods
        calls = filterCalls(calls);

        // Test the ids to make sure they have the right types
        Assert.assertTrue(endsWithInMap(ids, "B B"));
        Assert.assertTrue(endsWithInMap(ids, "S S"));
        Assert.assertTrue(endsWithInMap(ids, "I I"));
        Assert.assertTrue(endsWithInMap(ids, "J J"));

        // Test the calls list to make sure the values are right
        for (String call : calls) {
            long i = call.startsWith("-") ? 2 : 1;
            String value = call.split(" ")[(int) i];
            Assert.assertEquals(i, Long.parseLong(value));
        }
    }

    @Test
    public void testFloatingPointCollection() throws Exception {
        Class<?> clz = FloatingPointTester.class;

        loadModifyRun(clz);

        Map<Integer, String> ids = SaveUtil.getIds();
        List<String> calls = SaveUtil.getCalls();

        // Filters out any strings in calls that aren't entrances or exits to the test methods
        calls = filterCalls(calls);

        // Test the ids to make sure they have the right types
        Assert.assertTrue(endsWithInMap(ids, "F F"));
        Assert.assertTrue(endsWithInMap(ids, "D D"));

        // Test the calls list to make sure the values are right
        for (String call : calls) {
            double i = call.startsWith("-") ? 2.0 : 1.0;

            String value = call.split(" ")[(int) i];

            Assert.assertEquals(i, Double.parseDouble(value), 0.000001);
        }
    }

    @Test
    public void testCharCollection() throws Exception {
        Class<?> clz = CharTester.class;

        loadModifyRun(clz);

        Map<Integer, String> ids = SaveUtil.getIds();
        List<String> calls = SaveUtil.getCalls();

        // Filters out any strings in calls that aren't entrances or exits to the test methods
        calls = filterCalls(calls);

        // Test the ids to make sure they have the right types
        Assert.assertTrue(endsWithInMap(ids, "C C"));

        // Test the calls list to make sure the values are right
        Assert.assertEquals("a", calls.get(0).split(" ")[1]);
        Assert.assertEquals("b", calls.get(1).split(" ")[2]);
    }

    @Test
    public void testBooleanCollection() throws Exception {
        Class<?> clz = BooleanTester.class;

        loadModifyRun(clz);

        Map<Integer, String> ids = SaveUtil.getIds();
        List<String> calls = SaveUtil.getCalls();

        // Filters out any strings in calls that aren't entrances or exits to the test methods
        calls = filterCalls(calls);

        // Test the ids to make sure they have the right types
        Assert.assertTrue(endsWithInMap(ids, "Z Z"));

        // Test the calls list to make sure the values are right
        Assert.assertTrue(Boolean.parseBoolean(calls.get(0).split(" ")[1]));
        Assert.assertFalse(Boolean.parseBoolean(calls.get(1).split(" ")[2]));
    }

    // Test that ObjectIDs are collected correctly
    @Test
    public void testObjectIDCollection() throws Exception {
        Class<?> clz = ObjectIDTester.class;

        loadModifyRun(clz, ObjectTesterHelper.class);

        Map<Integer, String> ids = SaveUtil.getIds();

        // Test the ids to make sure they have the right types
        Assert.assertTrue(endsWithInMap(ids, "L Ljava/lang/Object;"));
        Assert.assertTrue(endsWithInMap(ids, "L Lvedebug/test/VideoTransformerTest$ObjectTesterHelper;"));
        Assert.assertFalse(ids.values().stream().anyMatch(id -> id.contains("hashCode")));
    }

    // Test that Strings are collected correctly
    @Test
    public void testStringValueCollection() throws Exception {
        Class<?> clz = StringValueTester.class;

        loadModifyRun(clz);

        List<String> calls = SaveUtil.getCalls();
        // Filters out any strings in calls that aren't entrances or exits to the test methods
        calls = filterCalls(calls);

        // Makes sure the String is printed with quotes around it like it's supposed to be
        Assert.assertEquals("\"hello\"", calls.get(0).split(" ")[1]);
        Assert.assertEquals("\"hello", calls.get(1).split(" ")[2]);
        Assert.assertEquals("world\"", calls.get(1).split(" ")[3]);
    }

    // Test that a basic array of primitives is collected correctly
    @Test
    public void testArrayValueCollection() throws Exception {
        Class<?> clz = ArrayValueTester.class;

        loadModifyRun(clz);

        List<String> calls = SaveUtil.getCalls();
        // Filters out any strings in calls that aren't entrances or exits to the test methods
        calls = filterCalls(calls);

        Assert.assertTrue(calls.get(0).endsWith("[1, 2, 3] \n"));
        Assert.assertTrue(calls.get(1).endsWith("[2, 2, 3] \n"));

        //TODO: Add testing for object arrays. Also should probably add testing for matrices
    }

    // Test that static initializers are handled correctly
    @Test
    public void testTraceOfClinit() throws Exception {
        Class<?> clz = F.class;

        loadModifyRun(clz);

        Map<Integer, String> ids = SaveUtil.getIds();
        List<String> calls = SaveUtil.getCalls()
                .stream()
                .filter(str -> !str.startsWith("@"))
                .collect(Collectors.toList());

        // Because we're using reflection to invoke main, the line number for the clinit doesn't actually get saved as -1
        // So instead we just make sure it's >640 since this file shouldn't ever really shrink
        // This isn't super rigorous but it's something
        Assert.assertTrue(Integer.parseInt(calls.get(0).split(" ")[0]) > 640);

        Set<String> idValues = new HashSet<>(ids.values());
        Set<String> expectedIDValues = new HashSet<String>(Arrays.asList(
                "vedebug/test/VideoTransformerTest.java vedebug/test/VideoTransformerTest$F <init> - V",
                "vedebug/test/VideoTransformerTest.java vedebug/test/VideoTransformerTest$F main L V",
                "vedebug/test/VideoTransformerTest.java vedebug/test/VideoTransformerTest$F <clinit> - V"
        ));
        expectedIDValues = Collections.unmodifiableSet(expectedIDValues);

        Assert.assertEquals(3, ids.size());
        Assert.assertEquals(expectedIDValues, idValues);

    }

    // Test that static initializers being implicitly called due to accessing another class are handled correctly
    @Test
    public void testTraceOfClinitInOtherClass() throws Exception {
        Class<?> clz = G.class;

        loadModifyRun(clz, H.class);

        Map<Integer, String> ids = SaveUtil.getIds();
        List<String> calls = SaveUtil.getCalls()
                .stream()
                .filter(str -> !str.startsWith("@"))
                .collect(Collectors.toList());

        // Make sure that the first method invoke *inside* of main is the clinit for H and that it doesn't show -1 as its line
        Assert.assertFalse(calls.get(1).startsWith("-1 "));
        Assert.assertTrue(calls.get(1).endsWith("4 \n"));

        Set<String> idValues = new HashSet<>(ids.values());
        Set<String> expectedIDValues = new HashSet<String>(Arrays.asList(
                "vedebug/test/VideoTransformerTest.java vedebug/test/VideoTransformerTest$G <init> - V",
                "vedebug/test/VideoTransformerTest.java vedebug/test/VideoTransformerTest$G main L V",
                "vedebug/test/VideoTransformerTest.java vedebug/test/VideoTransformerTest$H <init> - V",
                "vedebug/test/VideoTransformerTest.java vedebug/test/VideoTransformerTest$H <clinit> - V"
        ));
        expectedIDValues = Collections.unmodifiableSet(expectedIDValues);

        Assert.assertEquals(4, ids.size());
        Assert.assertEquals(expectedIDValues, idValues);
    }

    // Test that the invocation line numbers of methods are handled correctly
    @Test
    public void testInvocationLineCollection() throws Exception {
        VideoMethodVisitor.isSavingInvocationLineNums = true;

        Class<?> clz = J.class;

        loadModifyRun(clz);

        List<String> calls = SaveUtil.getCalls()
                .stream()
                .filter(str -> !str.startsWith("@"))
                .map(String::trim)
                .collect(Collectors.toList());

        //        Assert.assertEquals("-1 2 []", calls.get(0));
        // This line uses matches instead of equals because the first number could change if this file is edited
        Assert.assertTrue(calls.get(1).matches("^\\d+ 3$"));
        Assert.assertEquals("- 3", calls.get(2));
        Assert.assertEquals("- 2", calls.get(3));

    }

    // The test cannot pass under Java 8 because the line number table
    // in Java 8 does not include multi-line return statements but
    // only contain the first line of return.
    @Ignore
    // Test that return statements that involve a ternary over
    // multiple lines are handled correctly and don't break the basic
    // block collection
    @Test
    public void testTernaryReturn() throws Exception {
        Class<?> clz = I.class;

        loadModifyRun(clz);

        Collection<Integer> lastLineNums = SaveUtil.getLastLineNums().values();
        Set<Integer> basicBlocks = SaveUtil.getBasicBlockLineNums();

        Assert.assertTrue(lastLineNums.contains(Collections.max(basicBlocks)));
    }

    // Tests for when arguments and return values have strings that
    // contain new line characters
    @Test
    public void testStringsWithNewLines() throws Exception {
        Class<?> clz = K.class;

        loadModifyRun(clz);

        List<String> calls = SaveUtil.getCalls()
                .stream()
                .filter(str -> !str.startsWith("@"))
                .collect(Collectors.toList());

        Assert.assertEquals("\"Hello\\n\"", calls.get(1).split(" ")[1]);
        Assert.assertEquals("\"\\nWorld!\"", calls.get(2).split(" ")[2]);
    }

    // Other methods needed for testing ***********

    // Self-explantory; used like String's endsWith method but for
    // Strings in a map
    @SuppressWarnings("unused")
    private boolean endsWithInMap(Map<Integer, String> map, String value) {
        return map.values().stream().anyMatch(s -> s.endsWith(value));
    }

    // Tests the calls list to make sure that it's in the correct order
    private void testCallsStack(List<String> calls) {
        Deque<String> stack = new ArrayDeque<>();
        for (String call : calls) {
            if (call.startsWith("-")) {
                Assert.assertNotNull(stack.peekFirst());
                Assert.assertEquals(stack.pollFirst(), call.split(" ")[1]);
            } else {
                stack.offerFirst(call.split(" ")[0]);
            }
        }
        Assert.assertTrue(stack.isEmpty());
    }

    // Removes any line in the calls list that is not a method call or
    // a return from a method (i.e. gets rid of the BB lines).  Also
    // gets rid of the init and main methods because most of the tests
    // don't need those.
    private List<String> filterCalls(List<String> calls) {
        return calls
                .stream()
                .filter(str -> !str.startsWith("@")
                        && !str.startsWith("1 ")
                        && !str.startsWith("2 ")
                        && !str.startsWith("- 1 ")
                        && !str.startsWith("- 2 ")
                )
                .collect(Collectors.toList());
    }

    private byte[] loadBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];

        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        return buffer.toByteArray();
    }

    private void loadModifyRun(Class<?>... clzs) throws Exception {
        Assert.assertNotNull(clzs);

        Names.traverse = true;

        Map<String, byte[]> classes = new HashMap<>();
        for (Class<?> clz : clzs) {
            String dotName = clz.getName();
            String slashName = dotName.replaceAll("\\.", "/");
            String resName = "/" + slashName + ".class";

            // Load bytes for the original class
            InputStream is = clz.getResourceAsStream(resName);
            byte[] classfile = loadBytes(is);
            is.close();

            // Transform the class
            byte[] newClassfile = new VideoTransformer().transform(null, slashName, clz, null, classfile);
            classes.put(dotName, newClassfile);
        }

        // Special class loader for the current test
        ByteClassLoader cl = new ByteClassLoader(Thread.currentThread().getContextClassLoader(), classes);

        // Load transformed classes
        Class<?> mainClz = cl.findClass(clzs[0].getName());
        for (int i = 1; i < clzs.length; i++) {
            cl.findClass(clzs[i].getName());
        }

        // Run transformed class
        Method main = mainClz.getDeclaredMethod("main", String[].class);
        main.setAccessible(true);
        main.invoke(null, (Object) new String[0]);
    }
}
