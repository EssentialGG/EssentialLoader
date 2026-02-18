package gg.essential.loader.stage2.relaunch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
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

        // Inject extra mods which EssentialLoader has discovered outside the mod directory (e.g. Loader extracts
        // nested jars into the temporary files folder). If such jars contain mods/tweakers, Forge needs to explicitly
        // be told about them so it can load those mods/tweakers.
        // This should behave exactly like the `--mods` program argument which Forge adds (except that one is limited
        // to paths relative to the game directory, and such relative paths cannot be created on Windows when temp files
        // and game folder are on different drives).
        // ModListHelper is used on 1.8.9 and older 1.12.2 versions
        // LibraryManager is used on newer 1.12.2 versions
        if (name.equals("net.minecraftforge.fml.relauncher.ModListHelper")) {
            ClassNode classNode = new ClassNode(Opcodes.ASM5);
            new ClassReader(bytes).accept(classNode, 0);
            for (MethodNode method : classNode.methods) {
                if ("parseModList".equals(method.name)) {
                    InsnList instructions = method.instructions;
                    AbstractInsnNode ret = instructions.getLast();
                    while (ret.getOpcode() != Opcodes.RETURN) {
                        ret = ret.getPrevious();
                    }
                    instructions.insertBefore(ret, new FieldInsnNode(Opcodes.GETSTATIC, classNode.name, "additionalMods", "Ljava/util/Map;"));
                    instructions.insertBefore(ret, new MethodInsnNode(Opcodes.INVOKESTATIC, "gg/essential/loader/stage2/RelaunchedLoader", "injectExtraMods", "(Ljava/util/Map;)V", false));
                }
            }
            ClassWriter classWriter = new ClassWriter(Opcodes.ASM5);
            classNode.accept(classWriter);
            return classWriter.toByteArray();
        }
        if (name.equals("net.minecraftforge.fml.relauncher.libraries.LibraryManager")) {
            ClassNode classNode = new ClassNode(Opcodes.ASM5);
            new ClassReader(bytes).accept(classNode, 0);
            for (MethodNode method : classNode.methods) {
                if ("gatherLegacyCanidates"/*sic*/.equals(method.name)) {
                    InsnList instructions = method.instructions;
                    AbstractInsnNode ret = instructions.getLast();
                    while (ret.getOpcode() != Opcodes.ARETURN) {
                        ret = ret.getPrevious();
                    }
                    instructions.insertBefore(ret, new InsnNode(Opcodes.DUP));
                    instructions.insertBefore(ret, new MethodInsnNode(Opcodes.INVOKESTATIC, "gg/essential/loader/stage2/RelaunchedLoader", "injectExtraMods", "(Ljava/util/List;)V", false));
                }
            }
            ClassWriter classWriter = new ClassWriter(Opcodes.ASM5);
            classNode.accept(classWriter);
            return classWriter.toByteArray();
        }

        return bytes;
    }
}
