package me.hellrevenger.javadecompiler.ui

import com.strobel.assembler.metadata.JarTypeLoader
import com.strobel.assembler.metadata.MetadataSystem
import com.strobel.decompiler.DecompilationOptions
import com.strobel.decompiler.DecompilerSettings
import me.hellrevenger.javadecompiler.decompiler.LinkableTextOutput
import java.awt.Color
import java.io.File
import java.util.jar.JarFile
import javax.swing.BorderFactory
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel

val SUPPORT_FILES = arrayOf("jar", "class")

class FileTree(val settings: DecompilerSettings, val souceViewer: SouceViewer) : JTree() {
    val jars: Jars
    init {
        border = BorderFactory.createLineBorder(Color.GRAY)
        jars = Jars()
        model = DefaultTreeModel(jars)

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
                    val pane = JTextPane()
                    val output = LinkableTextOutput(pane)
                    settings.language.decompileType(resolve, output, DecompilationOptions())
                    output.flush()
                    val delimiter = path.lastIndexOf("/")
                    val className = if(delimiter == -1) path else path.substring(delimiter + 1)
                    val tab = JScrollPane(pane)
                    souceViewer.addTab(className, tab)
                    souceViewer.selectedComponent = tab
                }
            }

        }
        expandRow(0)
    }
}