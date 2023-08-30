package essential

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import org.gradle.api.file.FileTreeElement
import shadow.org.apache.tools.zip.ZipEntry
import shadow.org.apache.tools.zip.ZipOutputStream

/**
 * Shadow by default recursively explodes all jar files.
 * This works around that by renaming all jars to end with `.bundle` before shadow gets their hands on them and then
 * renaming them back with a transformer after shadow is done.
 */
fun ShadowJar.preserveJars() {
    rename { if (it.endsWith(".jar")) "$it.bundle" else it }
    transform(BundlingTransformer())
}

private class BundlingTransformer : Transformer {

    private val jars = mutableMapOf<String, ByteArray>()

    override fun getName(): String = "jar-bundling-transformer"

    override fun canTransformResource(element: FileTreeElement): Boolean = element.name.endsWith(".jar.bundle")

    override fun transform(context: TransformerContext) {
        jars[context.path] = context.`is`.readBytes()
    }

    override fun hasTransformedResource(): Boolean = jars.isNotEmpty()

    override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
        for ((path, bytes) in jars) {
            val entry = ZipEntry(path.removeSuffix(".bundle"))
            entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
            os.putNextEntry(entry)
            os.write(bytes)
        }
    }

}
