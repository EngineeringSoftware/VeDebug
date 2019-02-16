package vedebug.core;

import vedebug.asm.ClassVisitor;
import vedebug.asm.ClassWriter;
import vedebug.asm.MethodVisitor;
import vedebug.asm.Opcodes;

/**
 * This class visitor is only used to delegate the traversal to the
 * method visitor.
 *
 * @author Ben Buhse <bwbuhse@utexas.edu>
 * @author Thomas Wei <thomasw219@gmail.com>
 * @author Zhiqiang Zang <capapoc@gmail.com>
 */
public class VideoClassVisitor extends ClassVisitor {

    /**
     * Saving class name of the class being traversed
     */
    private final String className;

    private String source;
    private String fullSource;

    /**
     * Flag to indicate whether source is generated
     */
    private boolean isGenerated = false;

    public VideoClassVisitor(ClassWriter cw, String className) {
        super(Instr.ASM_API_VERSION, cw);
        this.className = className;
        this.source = null;
        this.fullSource = null;
    }

    @Override
    public void visitSource(String source, String debug) {
        if (!source.equals("<generated>")) {
            this.source = source;
            String sourceNoExt = source.substring(0, source.lastIndexOf('.'));
            String simpleClassName = className.substring(className.lastIndexOf('/') + 1);
            this.fullSource = className.substring(0, className.lastIndexOf(sourceNoExt)) + simpleClassName;
            SaveUtil.createBasicBlockFile(fullSource.replaceAll("/", "-"));
        } else {
            isGenerated = true;
        }
        super.visitSource(source, debug);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
        // This if statement keeps it from considering interfaces as
        // their own methods.
        // Keeps away from any abstract methods, which stop mv to
        // enter the following methods behind the first abstract one.
        if (!isGenerated && (access & Opcodes.ACC_SYNTHETIC) == 0
                && (access & Opcodes.ACC_ABSTRACT) == 0) {
            mv = new VideoMethodVisitor(mv, access, name, className, desc, source);
        }
        return mv;
    }

    @Override
    public void visitEnd() {
        if (!isGenerated) {
            SaveUtil.sortBasicBlocks(fullSource.replaceAll("/", "-"));
        }
        super.visitEnd();
    }
}
