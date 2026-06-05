import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.file.Files;
import java.nio.file.Paths;

public class Transform {
    public static void main(String[] args) throws Exception {
        ClassReader reader = new ClassReader(Files.readAllBytes(Paths.get(args[0])));
        ClassNode node = new ClassNode();
        reader.accept(node, 0);

        int before = node.methods.size();
        node.methods.removeIf(method -> method.name.equals("onInit") || method.name.equals("onItemDamageSet"));

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        Files.write(Paths.get(args[1]), writer.toByteArray());

        System.out.println("methods removed: " + (before - node.methods.size()));
        for (MethodNode method : node.methods) {
            System.out.println("  " + method.name + method.desc);
        }
    }
}
