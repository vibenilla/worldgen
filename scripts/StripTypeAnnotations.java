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

public final class StripTypeAnnotations {
    public static void main(String[] arguments) throws Exception {
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

        try (var zip = new ZipFile(arguments[0]);
             var output = new ZipOutputStream(Files.newOutputStream(Path.of(arguments[1])))) {
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
                    }
                }
                output.putNextEntry(new ZipEntry(entry.getName()));
                output.write(data);
                output.closeEntry();
            }
        }
    }
}
