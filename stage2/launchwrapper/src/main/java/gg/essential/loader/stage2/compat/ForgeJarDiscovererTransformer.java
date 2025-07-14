package gg.essential.loader.stage2.compat;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

// Forge's mod discovery code fails when trying to read Java 9 classes, unnecessarily spamming the log about them being
// "corrupt".
// This transformer excludes the Java 9 version specific jar directory from this search to avoid this spam.
//
// Functionally equivalent to this Mixin:
//   @Mixin(value = JarDiscoverer.class, remap = false)
//   public abstract class MixinJarDiscoverer {
//       @Redirect(method = {"discover", "findClassesASM"}, at = @At(value = "INVOKE", target = "Ljava/lang/String;startsWith(Ljava/lang/String;)Z"))
//       private boolean shouldSkip(String entry, String originalPattern) {
//           if (entry.startsWith("META-INF/versions/9/")) {
//               return true;
//           }
//           return entry.startsWith(originalPattern);
//       }
//   }
public class ForgeJarDiscovererTransformer implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if ("net.minecraftforge.fml.common.discovery.JarDiscoverer".equals(transformedName)) {
            ClassReader classReader = new ClassReader(basicClass);
            ClassNode classNode = new ClassNode();
            classReader.accept(classNode, 0);
            for (MethodNode method : classNode.methods) {
                if (method.name.equals("discover") || method.name.equals("findClassesASM")) {
                    InsnList insnList = method.instructions;
                    AbstractInsnNode insn = insnList.getFirst();
                    while (insn != null) {
                        if (insn instanceof MethodInsnNode) {
                            MethodInsnNode methodInsn = (MethodInsnNode) insn;
                            if (methodInsn.owner.equals("java/lang/String") && methodInsn.name.equals("startsWith")) {
                                injectExtraChecks(insnList, methodInsn);
                                break;
                            }
                        }

                        insn = insn.getNext();
                    }
                }
            }
            ClassWriter writer = new ClassWriter(0);
            classNode.accept(writer);
            return writer.toByteArray();
        }
        return basicClass;
    }

    private void injectExtraChecks(InsnList insnList, MethodInsnNode orgStartsWith) {
        InsnList before = new InsnList();
        InsnList after = new InsnList();

        // Stack: .., entry, needle
        before.add(new InsnNode(Opcodes.SWAP));
        // Stack: .., needle, entry
        before.add(new InsnNode(Opcodes.DUP_X1));
        // Stack: .., entry, needle, entry
        before.add(new InsnNode(Opcodes.SWAP));
        // Stack: .., entry, entry, needle
        // orgStartsWith
        // Stack: .., entry, boolean
        after.add(new InsnNode(Opcodes.SWAP));
        // Stack: .., boolean, entry
        after.add(new LdcInsnNode("META-INF/versions/9/"));
        // Stack: .., boolean, entry, needle
        after.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, orgStartsWith.owner, orgStartsWith.name, orgStartsWith.desc, false));
        // Stack: .., boolean, boolean
        after.add(new InsnNode(Opcodes.IOR));

        insnList.insertBefore(orgStartsWith, before);
        insnList.insert(orgStartsWith, after);
    }
}
