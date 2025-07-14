package essential

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.SimpleRemapper
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import java.nio.file.Path
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max

abstract class CompatMixinTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mixinClasses: ConfigurableFileCollection

    @get:InputFile
    abstract val input: RegularFileProperty

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun apply() {
        val excludedClasses = mutableSetOf(
            CompatMixin,
            CompatShadow,
            CompatAccessTransformer,
        )

        val mixins = mutableMapOf<String, Mixin>()
        for (classFile in this.mixinClasses.asFileTree.files) {
            if (classFile.extension != "class") {
                continue
            }

            val cls = ClassNode().apply { ClassReader(classFile.readBytes()).accept(this, 0) }
            val annotation = cls.invisibleAnnotations?.find { it.desc == CompatMixin.desc } ?: continue
            val args = annotation.args
            val target = args["value"]?.toString()?.removeSurrounding("L", ";")?.replace('/', '.')
                ?: args["target"]?.toString()
                ?: throw IllegalArgumentException("`@CompatMixin` annotation in $classFile is invalid.")

            if (target in mixins) {
                throw IllegalArgumentException("Multiple `@CompatMixin`s for \"$target\".")
            }
            mixins[target] = Mixin(classFile.toPath(), cls)
            excludedClasses += cls.name.replace('/', '.')
        }

        val mixinToTargetMapping = mixins.entries.associate { (target, mixin) ->
            mixin.node.name to target.replace('.', '/')
        }
        val mixinRemapper = SimpleRemapper(mixinToTargetMapping)

        ZipOutputStream(output.get().asFile.outputStream()).use { zipOut ->
            ZipInputStream(input.get().asFile.inputStream()).use { zipIn ->
                while (true) {
                    val inputEntry = zipIn.nextEntry ?: break
                    if (classForFile(inputEntry.name) in excludedClasses) {
                        continue
                    }

                    val outputEntry = ZipEntry(inputEntry.name)
                    outputEntry.time = CONSTANT_TIME_FOR_ZIP_ENTRIES
                    zipOut.putNextEntry(outputEntry)

                    val mixin = mixins.remove(classForFile(inputEntry.name))
                    if (mixin != null) {
                        val cls = ClassNode().apply { ClassReader(zipIn).accept(this, 0) }

                        merge(mixin.node, cls, mixinRemapper)

                        zipOut.write(ClassWriter(0).apply {
                            cls.accept(ClassRemapper(this, mixinRemapper))
                        }.toByteArray())
                    } else {
                        zipIn.copyTo(zipOut)
                    }

                    zipOut.closeEntry()
                }
            }
        }

        if (mixins.isNotEmpty()) {
            throw IllegalArgumentException(mixins.map { (cls, mixin) ->
                "Failed to find target \"$cls\" for \"${mixin.source}\""
            }.joinToString("\n"))
        }
    }

    private fun classForFile(path: String) = path
        .removeSuffix(".class")
        .replace('/', '.')
        .replace('\\', '.')

    private fun merge(mixin: ClassNode, cls: ClassNode, remapper: SimpleRemapper) {
        // Mixin targets Java 6, but we don't want to be as limited in terms of language features
        cls.version = Opcodes.V1_8

        // Process shadows first, before we add other methods (with potentially the same name)
        mixin.methods.removeIf { method ->
            val shadow = method.invisibleAnnotations?.find { it.desc == CompatShadow.desc }
                ?: return@removeIf false

            val originalName = shadow.args["original"]
            val targetName = originalName ?: method.name

            val mappedDesc = remapper.mapMethodDesc(method.desc)
            val targetMethod = cls.methods.find { it.name == targetName && it.desc == mappedDesc }
                ?: throw IllegalArgumentException("Could not find target method \"$targetName\" in ${cls.name}")

            if (originalName != null) {
                targetMethod.name = method.name
            }

            targetMethod.access = applyAccessTransformer(targetMethod.access, method.invisibleAnnotations)

            true
        }
        mixin.fields.removeIf { field ->
            val shadow = field.invisibleAnnotations?.find { it.desc == CompatShadow.desc }
                ?: return@removeIf false
            val originalName = shadow.args["original"]
            val targetName = originalName ?: field.name

            val mappedDesc = remapper.mapDesc(field.desc)
            val targetField = cls.fields.find { it.name == targetName && it.desc == mappedDesc }
                ?: throw IllegalArgumentException("Could not find target field \"$targetName\" in ${cls.name}")

            if (originalName != null) {
                throw UnsupportedOperationException("Renaming fields is not supported")
            }

            targetField.access = applyAccessTransformer(targetField.access, field.invisibleAnnotations)

            true
        }

        // Then merge the remaining methods and fields into the target class
        for (method in mixin.methods) {
            if (method.name == "<init>") {
                continue
            }

            if (method.name == "<clinit>") {
                val existingMethod = cls.methods.find { it.name == "<clinit>" }
                if (existingMethod != null) {
                    existingMethod.maxLocals = max(existingMethod.maxLocals, method.maxLocals)
                    existingMethod.maxStack = max(existingMethod.maxStack, method.maxStack)
                    existingMethod.instructions.remove(existingMethod.instructions.last.also { assert(it.opcode == Opcodes.RETURN) })
                    existingMethod.instructions.add(method.instructions)
                } else {
                    cls.methods.add(method)
                }
                continue
            }

            cls.methods.add(method)
        }
        for (field in mixin.fields) {
            if (field.name == "this$0") continue // synthetic accessor for outer class in inner class
            cls.fields.add(field)
        }

        // Apply access transformations
        cls.access = applyAccessTransformer(cls.access, mixin.invisibleAnnotations)

        // Merge interfaces
        for (itf in mixin.interfaces) {
            if (itf !in cls.interfaces) {
                cls.interfaces.add(itf)
            }
        }
    }

    private fun applyAccessTransformer(orgAccess: Int, annotations: List<AnnotationNode>?): Int {
        val annotation = annotations?.find { it.desc == CompatAccessTransformer.desc }
        return if (annotation != null) applyAccessTransformer(orgAccess, annotation) else orgAccess
    }

    private fun applyAccessTransformer(orgAccess: Int, accessTransformer: AnnotationNode): Int {
        var access = orgAccess
        (accessTransformer.args["add"] as? List<*>)?.forEach {
            access = access or it as Int
        }
        (accessTransformer.args["remove"] as? List<*>)?.forEach {
            access = access and (it as Int).inv()
        }
        return access
    }

    private val AnnotationNode.args get() = (values ?: emptyList()).chunked(2) { (k, v) -> k to v }.toMap()

    private val String.desc get() = "L${replace('.', '/')};"

    private data class Mixin(
        val source: Path,
        val node: ClassNode,
    )

    companion object {
        const val CompatMixin = "gg.essential.CompatMixin"
        const val CompatShadow = "gg.essential.CompatShadow"
        const val CompatAccessTransformer = "gg.essential.CompatAccessTransformer"

        // A safe, constant value for creating consistent zip entries
        // From: https://github.com/gradle/gradle/blob/d6c7fd470449a59fc57a26b4ebc0ad83c64af50a/subprojects/core/src/main/java/org/gradle/api/internal/file/archive/ZipCopyAction.java#L42-L57
        private val CONSTANT_TIME_FOR_ZIP_ENTRIES = GregorianCalendar(1980, Calendar.FEBRUARY, 1, 0, 0, 0).timeInMillis
    }
}