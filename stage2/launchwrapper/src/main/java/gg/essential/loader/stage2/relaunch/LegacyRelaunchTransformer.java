package gg.essential.loader.stage2.relaunch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.function.BiFunction;

import static gg.essential.loader.stage2.relaunch.Relaunch.FML_TWEAKER;

/**
 * For when we do not have access to Mixin 0.8 (old Essential version on 1.8.9).
 *
 * If we do have Mixin 0.8, we spin up an isolated instance of that instead and then a mixin takes care of this:
 * {@link gg.essential.loader.stage2.relaunch.mixins.forge.Mixin_SkipFMLSecurityManager}
 */
public class LegacyRelaunchTransformer implements BiFunction<String, byte[], byte[]> {
    @Override
    public byte[] apply(String name, byte[] bytes) {
        // Fuck forge
        // It installs a SecurityManager which locks itself down by rejecting any future managers and forge
        // itself refuses to boot if its manager is rejected (e.g. by a manager previously installed by it).
        if (name.equals(FML_TWEAKER)) {
            ClassNode classNode = new ClassNode(Opcodes.ASM5);
            new ClassReader(bytes).accept(classNode, 0);
            for (MethodNode method : classNode.methods) {
                InsnList instructions = method.instructions;
                for (int i = 0; i < instructions.size(); i++) {
                    AbstractInsnNode insn = instructions.get(i);
                    // This removes any call to setSecurityManager, instead dropping the security manager
                    if (insn instanceof MethodInsnNode && ((MethodInsnNode) insn).name.equals("setSecurityManager")) {
                        instructions.set(insn, new InsnNode(Opcodes.POP));
                    }
                }
            }
            ClassWriter classWriter = new ClassWriter(Opcodes.ASM5);
            classNode.accept(classWriter);
            return classWriter.toByteArray();
        }

        return bytes;
    }
}
