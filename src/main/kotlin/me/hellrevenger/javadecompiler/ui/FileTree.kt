package me.hellrevenger.javadecompiler.ui

import com.strobel.assembler.metadata.JarTypeLoader
import com.strobel.assembler.metadata.MetadataSystem
import com.strobel.decompiler.DecompilationOptions
import com.strobel.decompiler.DecompilerSettings
import com.strobel.decompiler.PlainTextOutput
import java.awt.Color
import java.io.File
import java.util.jar.JarFile
import javax.swing.BorderFactory
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel

val SUPPORT_FILES = arrayOf("jar", "class")

class FileTree(val settings: DecompilerSettings) : JTree() {
    val jars: Jars
    init {
        border = BorderFactory.createLineBorder(Color.GRAY)
        jars = Jars()
        model = DefaultTreeModel(jars)
        jars.model = model as DefaultTreeModel

        addFile(File("build/libs/JavaDecompiler-0.1.jar"))
    }


    fun addFile(file: File) {
        if(!SUPPORT_FILES.contains(file.extension.lowercase()) || !file.exists()) return
        if(file.extension.lowercase() == "jar") {
            jars.addJar(file)

            val system = MetadataSystem(JarTypeLoader(JarFile(file)))
            this.addTreeSelectionListener { event ->

                event.paths.forEachIndexed { i, it ->
                    if(!event.isAddedPath(i)) return@forEachIndexed

                    val path = if(it.pathCount > 2) {
                        val iterator = it.path.iterator()
                        iterator.next()
                        iterator.next()
                        iterator.asSequence().joinToString("/")
                    } else return@forEachIndexed // wtf
                    val lookup = system.lookupType(path) ?: return@forEachIndexed
                    val resolve = lookup.resolve() ?: return@forEachIndexed
                    val output = PlainTextOutput()
                    settings.language.decompileType(resolve, output, DecompilationOptions())
                    println(output.toString())
                }
            }

        }
        expandRow(0)
    }
}