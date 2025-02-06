package me.hellrevenger.javadecompiler.ui

import com.strobel.assembler.metadata.JarTypeLoader
import com.strobel.assembler.metadata.MetadataSystem
import com.strobel.decompiler.DecompilationOptions
import com.strobel.decompiler.DecompilerSettings
import me.hellrevenger.javadecompiler.decompiler.LinkableTextOutput
import java.awt.Color
import java.io.File
import java.util.jar.JarFile
import javax.swing.*
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.MutableTreeNode

val SUPPORT_FILES = arrayOf("jar", "class")

class FileTree : JTree() {
    val jars: Jars
    val systems = mutableSetOf<MetadataSystem>()

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
            systems.add(system)
            this.addTreeSelectionListener { event ->
                event.paths.forEachIndexed { i, it ->
                    if(!event.isAddedPath(i)) return@forEachIndexed

                    val path = if(it.pathCount > 2) {
                        val iterator = it.path.iterator()
                        iterator.next()
                        iterator.next()
                        iterator.asSequence().joinToString("/")
                    } else return@forEachIndexed // wtf
                    openClass(system, path)
                }
            }
            addTreeExpansionListener(ExpansionListener())
        }
        expandRow(0)
    }

    fun openClass(path: String): JTextPane? {
        systems.forEach {
            val pane = openClass(it, path)
            if(pane != null) return pane
        }
        return null
    }

    fun openClass(system: MetadataSystem, path: String): JTextPane? {
        val delimiter = path.lastIndexOf("/")
        val className = if(delimiter == -1) path else path.substring(delimiter + 1)

        val i = MainWindow.sourceViewer.indexOfTab(className)
        if(i != -1) return (MainWindow.sourceViewer.getTabComponentAt(i) as JScrollPane).viewport.view as JTextPane

        println("opening: $path")
        val lookup = system.lookupType(path) ?: return null
        val resolve = lookup.resolve() ?: return null
        val pane = JTextPane()
        val output = LinkableTextOutput(path, pane)
        MainWindow.settings.language.decompileType(resolve, output, DecompilationOptions())
        output.flush()
        val tabContent = JScrollPane(pane)
        MainWindow.sourceViewer.addTab(path, tabContent)
        MainWindow.sourceViewer.selectedComponent = tabContent

        return pane
    }
}

class ExpansionListener : TreeExpansionListener {
    override fun treeExpanded(event: TreeExpansionEvent){
        (event.path.lastPathComponent as? MutableTreeNode)?.let {
            if(it.childCount == 1) {
                MainWindow.fileTree?.expandPath(event.path.pathByAddingChild(it.getChildAt(0)))
            }
        }
    }

    override fun treeCollapsed(event: TreeExpansionEvent){

    }
}