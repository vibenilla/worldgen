import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.FieldTransform;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Strips type annotations from all classes in a jar so javac can read it.
 * Usage: java StripTypeAnnotations.java in.jar out.jar
 */
public final class StripTypeAnnotations {
    public static void main(String[] args) throws Exception {
        var classFile = ClassFile.of();
        var methodTransform = MethodTransform.dropping(element ->
                element instanceof RuntimeVisibleTypeAnnotationsAttribute
                        || element instanceof RuntimeInvisibleTypeAnnotationsAttribute);
        var fieldTransform = FieldTransform.dropping(element ->
                element instanceof RuntimeVisibleTypeAnnotationsAttribute
                        || element instanceof RuntimeInvisibleTypeAnnotationsAttribute);
        var classTransform = ClassTransform.dropping(element ->
                        element instanceof RuntimeVisibleTypeAnnotationsAttribute
                                || element instanceof RuntimeInvisibleTypeAnnotationsAttribute)
                .andThen(ClassTransform.transformingMethods(methodTransform))
                .andThen(ClassTransform.transformingFields(fieldTransform));

        try (var zip = new ZipFile(args[0]);
             var out = new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                var data = zip.getInputStream(entry).readAllBytes();
                if (entry.getName().endsWith(".class")) {
                    try {
                        data = classFile.transformClass(classFile.parse(data), classTransform);
                    } catch (Exception ignored) {
                        // keep original bytes if parsing fails
                    }
                }
                out.putNextEntry(new ZipEntry(entry.getName()));
                out.write(data);
                out.closeEntry();
            }
        }
        System.out.println("written " + args[1]);
    }
}
