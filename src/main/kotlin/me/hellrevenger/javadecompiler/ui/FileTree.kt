package me.hellrevenger.javadecompiler.ui

import com.strobel.assembler.InputTypeLoader
import com.strobel.assembler.metadata.JarTypeLoader
import com.strobel.assembler.metadata.MetadataSystem
import java.awt.Color
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDropEvent
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.io.File
import java.util.jar.JarFile
import javax.swing.*
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.MutableTreeNode

val SUPPORT_FILES = arrayOf("jar", "class")

class FileTree : JTree() {
    val filesNode: FilesNode
    val systems = hashMapOf<String, MetadataSystem>()
    val classFileSystem = MetadataSystem(InputTypeLoader())

    init {
        border = BorderFactory.createLineBorder(Color.GRAY)
        filesNode = FilesNode()
        model = DefaultTreeModel(filesNode)

        dropTarget = object : DropTarget(){
            override fun drop(event: DropTargetDropEvent) {
                try {
                    event.acceptDrop(DnDConstants.ACTION_COPY)
                    val files = event.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>

                    files.forEach { file -> addFile(file) }
                } catch (e: Exception) {
                    e.printStackTrace()
                    event.rejectDrop()
                }
            }
        }
        addTreeSelectionListener { event ->
            event.paths.forEachIndexed { i, it ->
                if(!event.isAddedPath(i)) return@forEachIndexed

                val path = if(it.pathCount > 2) {
                    val iterator = it.path.iterator()
                    iterator.next()
                    iterator.next()
                    iterator.asSequence().joinToString("/")
                } else {
                    ((it.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? ClassFileNode)?.file?.absolutePath ?: return@forEachIndexed
                }
                waitCursor {
                    openClass(path)
                }
            }
        }
        addTreeExpansionListener(ExpansionListener())
        addMouseListener(FileMouseListener(this))
        addMouseMotionListener(ToolTipListener(this))

        addFile(File("build/libs/JavaDecompiler-0.1.1.jar"))
    }

    fun addFile(file: File) {
        if(!SUPPORT_FILES.contains(file.extension.lowercase()) || !file.exists()) return
        if(file.extension.lowercase() == "jar") {
            if(!filesNode.addJar(file)) return

            val system = MetadataSystem(JarTypeLoader(JarFile(file)))
            systems[file.absolutePath] = system
        } else if (file.extension.lowercase() == "class") {
            if(!filesNode.addClass(file)) return
        }
        expandRow(0)
        (model as? DefaultTreeModel)?.nodeStructureChanged(filesNode)
    }

    fun openClass(path: String): JTextPane? {
        systems.forEach {
            val pane = MainWindow.sourceViewer.openClass(it.key, it.value, path)
            if(pane != null) return pane
        }
        if(path.lowercase().endsWith(".class")) {
            return MainWindow.sourceViewer.openClass(path, classFileSystem, path)
        }
        return null
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

    override fun treeCollapsed(event: TreeExpansionEvent){ }
}

class ToolTipListener(val tree: FileTree) : MouseMotionListener {
    override fun mouseDragged(e: MouseEvent?) { }

    override fun mouseMoved(e: MouseEvent) {
        val row = tree.getRowForLocation(e.x, e.y)
        if(row == -1) return
        val path = tree.getPathForRow(row)
        if(path.pathCount == 2) {
            tree.toolTipText = ((path.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? JarNode)?.getPath()
        } else {
            tree.toolTipText = null
        }
    }
}

class FileMouseListener(val tree: FileTree) : MouseListener {
    override fun mouseClicked(e: MouseEvent) {
        if(e.button != 3) return
        val row = tree.getClosestRowForLocation(e.x, e.y)
        if(row == -1) return
        val path = tree.getPathForRow(row)
        if(path.pathCount == 2) {
            (path.lastPathComponent as? DefaultMutableTreeNode)?.let {
                val menu = JPopupMenu()
                menu.invoker = tree
                menu.setLocation(e.xOnScreen, e.yOnScreen)
                menu.add("").action = object : AbstractAction("Full Scan") {
                    override fun actionPerformed(e: ActionEvent) {
                        val abso = (it.userObject as? JarNode)?.getPath() ?: return
                        MainWindow.analyzer.scanJar(abso)
                    }
                }
                menu.add("")
                menu.add("").action = object : AbstractAction("Close") {
                    override fun actionPerformed(e: ActionEvent) {
                        val abso = (it.userObject as? ClassFileNode)?.file?.absolutePath ?: (it.userObject as? JarNode)?.getPath() ?: return
                        MainWindow.sourceViewer.onFileRemoved(abso)
                        tree.filesNode.removeFile(abso)
                        (tree.model as? DefaultTreeModel)?.nodeStructureChanged(tree.filesNode)
                    }
                }
                menu.isVisible = true
            }
        } else if(path.pathCount > 2) {
            (path.getPathComponent(1) as? DefaultMutableTreeNode)?.let {
                val iterator = path.path.iterator()
                iterator.next()
                iterator.next()
                val path = iterator.asSequence().joinToString("/")

                val menu = JPopupMenu()
                menu.invoker = tree
                menu.setLocation(e.xOnScreen, e.yOnScreen)
                menu.add("").action = object : AbstractAction("Scan") {
                    override fun actionPerformed(e: ActionEvent) {
                        val abso = (it.userObject as? JarNode)?.getPath() ?: return
                        MainWindow.analyzer.scanJar(abso, path)
                    }
                }
                menu.isVisible = true
            }
        }
    }

    override fun mousePressed(e: MouseEvent) {
    }

    override fun mouseReleased(e: MouseEvent?) {
    }

    override fun mouseEntered(e: MouseEvent?) {
    }

    override fun mouseExited(e: MouseEvent?) {
    }
}