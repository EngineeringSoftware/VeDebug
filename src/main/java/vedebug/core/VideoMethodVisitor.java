package vedebug.core;

import vedebug.asm.Label;
import vedebug.asm.MethodVisitor;
import vedebug.asm.Opcodes;
import vedebug.asm.Type;
import vedebug.util.Types;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static vedebug.asm.Opcodes.INVOKESTATIC;

/**
 * Used to instrument methods to collect basic blocks and other meta
 * info needed for the time travel debugging.
 *
 * @author Ben Buhse <bwbuhse@utexas.edu>
 * @author Thomas Wei <thomasw219@gmail.com>
 * @author Zhiqiang Zang <capapoc@gmail.com>
 */
public class VideoMethodVisitor extends MethodVisitor {

    private final Pattern TYPES_PATTERN = Pattern.compile("\\[*L[^;]+;" +
            "|\\[[ZBCSIFDJ]|[ZBCSIFDJ]"); //Regex for desc \[*L[^;]+;
    // |\[[ZBCSIFDJ]|[ZBCSIFDJ]

    // Used as a flag in testing
    public static boolean isSavingInvocationLineNums = true;

    // Used as a flag to detect if super() or this() happens
    private static boolean isCallingSuperOrThis = false;

    // Temporarily store the first line number of a method, which is
    // used for handling some constructors that call the method
    // signature line first
    private static int firstLineNumberBackup = 0;
    private static int firstBasicBlockLineNumberBackup = 0;

    // Not every method requires visitCode() to be called, but since
    // that's where data is collected this only gets incremented in
    // that method
    private static int idCounter = 1;

    private static String pkg = null;

    // The method's id.  This would preferably be final but then there
    // are issues with skipped IDs due to not all of them requiring
    // visitCode()
    private int id;

    // Information about the class and method that is needed in
    // visitCode()
    private final int access;
    private final String className;
    private final String methodName;
    private final String desc;
    private final String source;
    private final String returnType;

    // This is set to true once visitLineNumber() has been called once
    // in each method so that only the first line is recorded
    private boolean visitedFirstLine;
    private boolean visitedInitFirstLine;

    // Used for tracking basicBlocks.  For this to work we had to make
    // lineNumber in the Label class public
    private boolean isGettingLineNumber;
    // Used in visitLineNumber() to get
    // the line # after certain instructions

    // This is a set of strings since the method ID is included at the
    // beginning of the BB line number
    private final Set<Integer> basicBlockLineNums;
    // Line numbers where all basic block leaders are

    // This is used to add basic block line numbers after the else
    // parts of if-else statements etc.
    private final Set<Integer> basicBlocksToCollect;

    // Keeps track of all line numbers from visitLineNumber but is
    // only used whenever a method is invoked
    private int currentLineNumber;

    // This gets called when the VideoTransformer is constructed,
    // should only be set once
    static void setPkg(String pkg) {
        VideoMethodVisitor.pkg = pkg;
    }

    /**
     * Constructor.
     */
    VideoMethodVisitor(MethodVisitor mv, int access, String methodName,
                       String className, String desc, String source) {
        super(Instr.ASM_API_VERSION, mv);

        // This is -1 because it can't actually be set until later
        this.id = -1;
        this.access = access;
        this.methodName = methodName;
        this.className = className;
        this.desc = desc;
        this.source = source;
        this.returnType = Type.getReturnType(desc).toString();
        this.visitedFirstLine = false;
        this.visitedInitFirstLine = false;
        this.isGettingLineNumber = false;
        this.basicBlockLineNums = new TreeSet<>();
        this.basicBlocksToCollect = new HashSet<>();
        this.currentLineNumber = -1;
    }

    /**
     * This method will alter each method so that when it's called it
     * will add its name and any arguments to the list in SaveUtil
     * which is saved into a file at the end of the execution.
     */
    @Override
    public void visitCode() {
        // If this method is a static initializer block then save the
        // line number where it was invoked from; otherwise save
        // whatever parameters were passed to the method even though
        // every method gets its invocation line number saved now,
        // <clinit> needs this because static initializers are handled
        // differently than regular methods.
        if (methodName.equals("<clinit>")) {
            // Backup the invocation line number of a static method
            mv.visitMethodInsn(INVOKESTATIC, Names.SAVE_UTIL_INTERNAL, "backupInvocationLineNumber", "()V", false);

            // Save the invocation line number
            mv.visitMethodInsn(INVOKESTATIC, Names.SAVE_UTIL_INTERNAL, "saveInvocationLineNumber", "()V", false);
        }

        // Set default value for the flag
        isCallingSuperOrThis = false;

        // Save the id for this method.  Does this here because not
        // every method actually gets to visitCode(), but this is
        // where methods get saved. So if it incremented in the
        // constructor then some IDs would be skipped, which causes
        // bugs in the trace completion.
        id = idCounter++;

        // These two variables make a nice char[] of param types and
        // then also give the number of parameters.
        char[] methodDescParsed = parseMethodArguments();
        int numParams = methodDescParsed.length;

        // Saves where the methodID was created.
        SaveUtil.insertMethodID(id, className.substring(0, className
                .lastIndexOf("/")) + "/" + source
                + " " + className + " " + methodName + " " + (numParams > 0 ?
                String.valueOf(methodDescParsed) : "-")
                + " " + returnType);

        // Inserts code to add the method's id to the logging file.
        mv.visitLdcInsn(id + "");
        mv.visitMethodInsn(INVOKESTATIC, Names.SAVE_UTIL_INTERNAL, Names.SAVE_METHOD, "" +
                "(Ljava/lang/String;)V", false);

        // If the method is not a static initializer then get its
        // parameter values.
        if (!methodName.equals("<clinit>")) {
            captureParameterValues(numParams, methodDescParsed);
        }

        super.visitCode();
    }

    @Override
    public void visitLineNumber(int line, Label start) {
        // This if statement is where the first line number of every
        // method is saved.
        if (!visitedFirstLine) {
            visitedFirstLine = true;
            // For everything except constructors, the first
            // visitLineNumber() call is the line which is saved.
            firstLineNumberBackup = line;
            firstBasicBlockLineNumberBackup = (int) start.lineNumber;
            if (!methodName.equals("<init>")) {
                SaveUtil.insertLineNum(id, line);
                basicBlockLineNums.add((int) start.lineNumber);
            }
        } else {
            // For the constructors except those calling super() or
            // this(), the second visitLineNumber() call is saved
            // because constructors have a call on the method
            // signature line. We'll make modifications to
            // constructors that call super() or this() in visitEnd()
            if (methodName.equals("<init>") && !visitedInitFirstLine) {
                visitedInitFirstLine = true;
                SaveUtil.insertLineNum(id, line);
                basicBlockLineNums.add((int) start.lineNumber);
            }
        }

        // This is where the last line number of every method is
        // saved.  Since it gets saved every time, it'll only
        // permanently save the line # of the last line visited.
        SaveUtil.insertLastLineNum(id, line);

        if (isGettingLineNumber || basicBlocksToCollect.contains(line)) {
            // Can always be called since it just returns null if line wasn't
            // in it
            basicBlocksToCollect.remove(line);
            basicBlockLineNums.add((int) start.lineNumber);
            mv.visitLdcInsn("@" + id + ":" + start.lineNumber);
            mv.visitMethodInsn(INVOKESTATIC, Names.SAVE_UTIL_INTERNAL, Names.SAVE_METHOD, "" +
                    "(Ljava/lang/String;)V", false);
            isGettingLineNumber = false;
        }

        currentLineNumber = line;
        super.visitLineNumber(line, start);
    }

    // Used to add save() calls at any return instructions so that the
    // stack trace can be stored in MethodCalls.txt.
    @Override
    public void visitInsn(int opcode) {
        switch (opcode) {
            case Opcodes.IRETURN:
            case Opcodes.FRETURN:
            case Opcodes.ARETURN:
            case Opcodes.DRETURN:
            case Opcodes.LRETURN:
            case Opcodes.RETURN:
            case Opcodes.RET:
                mv.visitLdcInsn("- " + id);
                mv.visitMethodInsn(INVOKESTATIC, Names.SAVE_UTIL_INTERNAL, Names.SAVE_METHOD, "" +
                        "(Ljava/lang/String;)V", false);
                // Don't need to capture the return value if the type
                // is void.
                if (!returnType.equals("V")) {
                    captureReturnValue();
                }
                // Recover the invocation line number of a static method from the backup
                // if the method is clinit
                if (methodName.equals("<clinit>")) {
                    mv.visitMethodInsn(INVOKESTATIC, Names.SAVE_UTIL_INTERNAL,
                            "recoverInvocationLineNumber", "()V", false);
                }
                // break is intentionally not used here
            case Opcodes.LCMP:
            case Opcodes.FCMPL:
            case Opcodes.FCMPG:
            case Opcodes.DCMPL:
            case Opcodes.DCMPG:
                isGettingLineNumber = true;
                // All of these opcodes signify the end of a basic
                // block, so the next line is saved instead of the
                // current one.
                break;
        }

        super.visitInsn(opcode);
    }

    // Table Switch instructions occur on switch-cases without many
    // "empty" spots, i.e. the cases are 1...5.  This will collect
    // their basic blocks to save to the file as well as add calls to
    // save when their BBs are accessed.
    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        for (Label label : checkIfLabelsAreSafe(labels)) {
            basicBlockLineNums.add((int) label.lineNumber);
        }
        basicBlockLineNums.add((int) dflt.lineNumber);
        isGettingLineNumber = true;
        super.visitTableSwitchInsn(min, max, dflt, labels);
    }

    // Lookup Switch instructions occur on switch-cases with many
    // "empty" spots, i.e. the cases are 1, 100, 1000.  This will
    // collect their basic blocks to save to the file as well as add
    // calls to save when their BBs are accessed.
    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        for (Label label : checkIfLabelsAreSafe(labels)) {
            basicBlockLineNums.add((int) label.lineNumber);
        }
        basicBlockLineNums.add((int) dflt.lineNumber);
        isGettingLineNumber = true;
        super.visitLookupSwitchInsn(dflt, keys, labels);
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        isGettingLineNumber = true;
        basicBlocksToCollect.add((int) label.lineNumber);
        super.visitJumpInsn(opcode, label);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String
            descriptor, boolean isInterface) {
        // If the method being called is actually just accessing a variable
        // in a static inner class, then ignore it
        // When running tests we don't want the invocation line numbers to be
        // collected so just skip out here
        // If the method being called isn't being instrumented then ignore it
        if (!isSavingInvocationLineNums || name.matches("^access\\$\\d\\d\\d$")) {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            return;
        } else if (pkg != null) {
            // This needs to be inside the branch so that the else is
            // not reached whenever pkg is not null.
            if (!owner.startsWith(pkg)) {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                return;
            }
        } else {
            // Only check these if pkg is null AND the other
            // conditions for saving the invocation line number are
            // met; otherwise we don't want to waste time checking
            // them.

            if (Types.isIgnoredClassName(owner)) {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                return;
            }
        }

        // Mark a flag for the constructors that include super() or this()
        if (methodName.equals("<init>")) {
            isCallingSuperOrThis = name.equals("<init>");
        }

        // Otherwise save the line number where it's currently being
        // invoked from.
        mv.visitLdcInsn(currentLineNumber);
        mv.visitMethodInsn(INVOKESTATIC, Names.SAVE_UTIL_INTERNAL, "setCurrentInvocationLine", "(I)V", false);
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    @Override
    public void visitEnd() {
        // Deal with wrong line numbers regrading to the constructors that include super() or this()
        if (isCallingSuperOrThis) {
            SaveUtil.replaceLineNum(id, firstLineNumberBackup);
            basicBlockLineNums.add(firstBasicBlockLineNumberBackup);
        }

        // Remove any zero from the basicBlockLineNums set.
        basicBlockLineNums.remove(0);

        // Then print the basic blocks to the file.  The stream gets
        // rid of any BBs that were saved that simply said line number
        // 0.
        String sourceNoExt = source.substring(0, source.lastIndexOf('.'));
        String fullSource = className.substring(0, className.lastIndexOf(sourceNoExt)) + sourceNoExt;
        SaveUtil.printBasicBlocksToFile(fullSource.replaceAll("/", "-"), basicBlockLineNums);

        // If the return is multiple lines then this will make sure to
        // get whatever the last line in it is.
        SaveUtil.setActualLastLineNum(id);

        super.visitEnd();
    }

    // This is used in visitTableSwitchInsn and visitLookupSwitchInsn
    // to verify that the labels parameter is not null.
    private Label[] checkIfLabelsAreSafe(Label[] labels) {
        return (labels == null) ? new Label[]{} : labels;
    }

    private void captureParameterValues(int numParams, char[] methodDescParsed) {
        try {
            captureParameterValues0(numParams, methodDescParsed);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Insert code that captures whatever parameters are being passed
    // to the method and saves them to the list in SaveUtil.
    private void captureParameterValues0(int numParams, char[]
            methodDescParsed) {
        // Used to deal with the implicit param in instance
        // methods that is not present in static methods.
        int OFFSET = (Opcodes.ACC_STATIC & access) != 0 ? 0 : 1;

        // Increases by one whenever longs or doubles are dealt
        // with since they take 2 spots in the local vars area.
        // numParams is incremented at the same time for the
        // purposes of the for loop working.
        int COUNT = 0;

        // This loop is where the parameter values are actually
        // loaded then added to the list.
        for (int i = 0; i + COUNT < numParams; i++) {
            int INDEX = i + OFFSET + COUNT;

            // Each of these will load the parameter to the JVM
            // stack frame and then call our save() method on it.
            switch (methodDescParsed[i]) {
                // byte, short, and int can all use the same method
                case 'B':
                case 'S':
                case 'I':
                    mv.visitVarInsn(Opcodes.ILOAD, INDEX);
                    mv.visitMethodInsn(INVOKESTATIC, Names.SAVE_UTIL_INTERNAL, Names.SAVE_METHOD, "(I)V", false);
                    break;
                // boolean and char could use the int method too,
                // but they'd show up wrong, so they have their
                // own
                case 'Z':
                    mv.visitVarInsn(Opcodes.ILOAD, INDEX);
                    mv.visitMethodInsn(INVOKESTATIC, Names.SAVE_UTIL_INTERNAL, Names.SAVE_METHOD, "(Z)V", false);
                    break;
                case 'C':
                    mv.visitVarInsn(Opcodes.ILOAD, INDEX);
                    mv.visitMethodInsn(INVOKESTATIC, Names.SAVE_UTIL_INTERNAL, Names.SAVE_METHOD, "(C)V", false);
                    break;
                case 'J':
                    mv.visitVarInsn(Opcodes.LLOAD, INDEX);
                    mv.visitMethodInsn(INVOKESTATIC, Names.SAVE_UTIL_INTERNAL, Names.SAVE_METHOD, "(J)V", false);
                    COUNT++;
                    numParams++;
                    break;
                case 'F':
                    mv.visitVarInsn(Opcodes.FLOAD, INDEX);
                    mv.visitMethodInsn(INVOKESTATIC, Names.SAVE_UTIL_INTERNAL, Names.SAVE_METHOD, "(F)V", false);
                    break;
                case 'D':
                    mv.visitVarInsn(Opcodes.DLOAD, INDEX);
                    mv.visitMethodInsn(INVOKESTATIC, Names.SAVE_UTIL_INTERNAL, Names.SAVE_METHOD, "(D)V", false);
                    COUNT++;
                    numParams++;
                    break;
                case 'L':
                    mv.visitVarInsn(Opcodes.ALOAD, INDEX);
                    mv.visitMethodInsn(INVOKESTATIC, Names.SAVE_UTIL_INTERNAL, Names.SAVE_METHOD,
                            "(Ljava/lang/Object;)V", false);
                    break;
                default:
                    System.err.println("Saving the parameters broke, your " +
                            "stack may be messed up.");
                    break;
            }
        }
    }

    private void captureReturnValue() {
        // This if statement duplicates the return value that's
        // already on the stack so it can be used in the save()
        // method.  Long and Double need to use DUP2 since they take
        // up 2 words
        if (returnType.equals("J") || returnType.equals("D")) {
            mv.visitInsn(Opcodes.DUP2);
        } else {
            mv.visitInsn(Opcodes.DUP);
        }

        try {
            captureReturnValue0();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Insert code that captures whatever value is being returned and
    // saves it to the list in SaveUtil.
    private void captureReturnValue0() {
        switch (returnType.charAt(0)) {
            // byte, short, and int can all use the same method
            // like before
            case 'B':
            case 'S':
            case 'I':
                mv.visitMethodInsn(INVOKESTATIC, Names.SAVE_UTIL_INTERNAL, Names.SAVE_METHOD, "(I)V", false);
                break;
            // boolean and char could use the int method too, but
            // they'd show up wrong, so they have their own
            case 'Z':
                mv.visitMethodInsn(INVOKESTATIC, Names.SAVE_UTIL_INTERNAL, Names.SAVE_METHOD, "(Z)V", false);
                break;
            case 'C':
                mv.visitMethodInsn(INVOKESTATIC, Names.SAVE_UTIL_INTERNAL, Names.SAVE_METHOD, "(C)V", false);
                break;
            case 'J':
                mv.visitMethodInsn(INVOKESTATIC, Names.SAVE_UTIL_INTERNAL, Names.SAVE_METHOD, "(J)V", false);
                break;
            case 'F':
                mv.visitMethodInsn(INVOKESTATIC, Names.SAVE_UTIL_INTERNAL, Names.SAVE_METHOD, "(F)V", false);
                break;
            case 'D':
                mv.visitMethodInsn(INVOKESTATIC, Names.SAVE_UTIL_INTERNAL, Names.SAVE_METHOD, "(D)V", false);
                break;
            // These two are together because arrays are objects but
            // start with [ instead of L; parseMethodArguments() deals
            // with this fact in captureParameterValues()
            case 'L':
            case '[':
                mv.visitMethodInsn(INVOKESTATIC, Names.SAVE_UTIL_INTERNAL, Names.SAVE_METHOD, "" +
                        "(Ljava/lang/Object;)V", false);
                break;
            default:
                System.err.println("Saving the return type broke, your stack may be messed up");
                break;
        }
    }

    // Actually, ASM provided a static method called
    // getArgumentTypes(String methodDesc) in class Type to do the
    // same thing.  Not sure if these two methods should have their
    // own class, but they're only used here.  They're for parsing the
    // method desc and returning a char[] with the types of each
    // param.
    private char[] parseMethodArguments() {
        String[] splitDesc = splitMethodDesc();
        char[] returnChars = new char[splitDesc.length];
        int count = 0;
        for (String type : splitDesc) {
            // Marks all Objects (including arrays) as references
            if (type.startsWith("L") || type.startsWith("[")) {
                returnChars[count] = 'L';
            } else {
                if (type.length() > 1) {
                    throw new RuntimeException();
                }
                returnChars[count] = type.charAt(0);
            }
            count += 1;
        }
        return returnChars;
    }

    // Uses some fancy regex to split the desc string.
    private String[] splitMethodDesc() {
        int arraylen = Type.getArgumentTypes(desc).length;
        int beginIndex = desc.indexOf('(');
        int endIndex = desc.lastIndexOf(')');
        if ((beginIndex == -1 && endIndex != -1) || (beginIndex != -1 &&
                endIndex == -1)) {
            System.err.println(beginIndex);
            System.err.println(endIndex);
            throw new RuntimeException();
        }
        String x0;
        if (beginIndex == -1) {
            x0 = desc;
        } else {
            x0 = desc.substring(beginIndex + 1, endIndex);
        }

        Matcher matcher = TYPES_PATTERN.matcher(x0);
        String[] listMatches = new String[arraylen];
        int counter = 0;
        while (matcher.find()) {
            listMatches[counter] = matcher.group();
            counter += 1;
        }
        return listMatches;
    }

    // Used to reset the IDCounter, mostly for testing
    static void resetIDCounter() {
        idCounter = 1;
    }
}
