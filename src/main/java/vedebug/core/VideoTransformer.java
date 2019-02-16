package vedebug.core;

import vedebug.asm.ClassReader;
import vedebug.asm.ClassWriter;
import vedebug.util.Types;

import java.lang.instrument.ClassFileTransformer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;

import java.io.DataOutputStream;
import java.io.FileOutputStream;

/**
 * Transformer that ensures that we invoke the class visitor on all
 * relevant classes.
 *
 * @author Ben Buhse <bwbuhse@utexas.edu>
 * @author Thomas Wei <thomasw219@gmail.com>
 * @author Zhiqiang Zang <capapoc@gmail.com>
 */
public class VideoTransformer implements ClassFileTransformer {

    /**
     * Instrument classes on the given path (if != null).
     */
    private final Path path;

    /**
     * Instrument classes in the given package (if != null).
     */
    private final String pkg;

    /**
     * Only search for files with the given extensions (if != null)
     * If this is not set then Vedebug will only look for .java files.
     */
    private final String[] fileExtensions;

    /**
     * Constructor.
     */
    public VideoTransformer() {
        this(null, null, null);
    }

    /**
     * Constructor.
     */
    public VideoTransformer(Path path, String pkg, String[] fileExtensions) {
        this.path = path;
        this.pkg = pkg;
        this.fileExtensions = fileExtensions;

        VideoMethodVisitor.setPkg(pkg);
        SaveUtil.setPkg(pkg);
    }

    /**
     * Filter classes and use different instrumentation strategy
     * according to the argument passed to javaagent.
     */
    @Override
    public byte[] transform(ClassLoader classLoader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {

        // Skip classes in our black list
        try {
            if (Types.isIgnoredClassName(className)) {
                return null;
            }
        } catch (NullPointerException ex) {
            // Lambda syntax causes null className
            return null;
        }

        // If path isn't null then a directory to transform was set so
        // it uses this if block to determine whether or not to
        // transform a certain class.  Doesn't work if somebody used $
        // in classNames but that's bad practice.
        if (pkg != null) {
            if (!className.startsWith(pkg)) {
                return null;
            }
        } else if (path != null) {
            String classNameToCheck = className;

            // If it has a dollar sign that means it's a nested class
            // so you need to get rid of everything after the first
            // dollar sign to get the source file.
            // Some Scala .class files actually end with a $, but this still works
            if (classNameToCheck.contains("$")) {
                classNameToCheck = classNameToCheck.substring(0, classNameToCheck.indexOf("$"));
            }

            // If the file doesn't exist in the path then return null,
            // otherwise go on to transform.
            // TODO: Make this work for Scala (or any other JVM lang)
            // files.
            if (fileExtensions == null) {
                if (!Files.exists(Paths.get(path.toString() + "/" + classNameToCheck + ".java"))) {
                    return null;
                }
            } else {
                boolean found = false;
                for (String fileExtension : fileExtensions) {
                    if (Files.exists(Paths.get(path.toString() + "/" + classNameToCheck + "." + fileExtension))) {
                        found = true;
                    }
                }
                if (!found) {
                    return null;
                }
            }
        }

        // Transform a class.
        try {
            return transformClass(classLoader, className, classfileBuffer);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    // INTERNAL

    /**
     * Instrument a class.
     */
    private byte[] transformClass(ClassLoader loader, String className, byte[] classfileBuffer) {
        // ASM Code
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        VideoClassVisitor visitor = new VideoClassVisitor(writer, className);
        reader.accept(visitor, 0);
        // Statement just used for debugging purposes
        // saveClassfileBufferForDebugging(className, writer.toByteArray());
        return writer.toByteArray();
    }

    /**
     * This method is only used for debugging.  The method saves the
     * bytecode of a class with hardcoded name into the out file.  One
     * can use javap to check the content of the instrumented code.
     */
    @SuppressWarnings("unused")
    private void saveClassfileBufferForDebugging(String className, byte[] classfileBuffer) {
        try {
            if (className.contains("Bug")) {
                DataOutputStream tmpout = new DataOutputStream(new FileOutputStream("classfile-buffer-output"));
                tmpout.write(classfileBuffer, 0, classfileBuffer.length);
                tmpout.close();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
