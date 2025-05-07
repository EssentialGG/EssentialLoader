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
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.function.BiFunction;

import static gg.essential.loader.stage2.relaunch.Relaunch.FML_TWEAKER;

public class RelaunchTransformer implements BiFunction<String, byte[], byte[]> {
    @Override
    public byte[] apply(String name, byte[] bytes) {
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

        // Suppress the stage1 update mechanism in any stage0 classes because it'll fail on Windows where the file
        // is already locked by the JVM.
        if (name.endsWith(".EssentialSetupTweaker")) {
            ClassNode classNode = new ClassNode(Opcodes.ASM5);
            new ClassReader(bytes).accept(classNode, 0);
            for (MethodNode method : classNode.methods) {
                if ("loadStage1".equals(method.name)) {
                    // Older stage0 versions have a static method, in newer ones it's a member method.
                    boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
                    InsnList instructions = method.instructions;
                    instructions.clear();
                    instructions.add(new TypeInsnNode(Opcodes.NEW, "gg/essential/loader/stage1/EssentialSetupTweaker"));
                    instructions.add(new InsnNode(Opcodes.DUP));
                    instructions.add(new VarInsnNode(Opcodes.ALOAD, isStatic ? 0 : 1));
                    instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "gg/essential/loader/stage1/EssentialSetupTweaker", "<init>", "(Lnet/minecraft/launchwrapper/ITweaker;)V", false));
                    instructions.add(new InsnNode(Opcodes.ARETURN));
                    method.maxLocals = isStatic ? 1 : 2;
                    method.maxStack = 3;
                    method.localVariables.clear();
                    method.tryCatchBlocks.clear();
                }
            }
            ClassWriter classWriter = new ClassWriter(Opcodes.ASM5);
            classNode.accept(classWriter);
            return classWriter.toByteArray();
        }

        return bytes;
    }
}
