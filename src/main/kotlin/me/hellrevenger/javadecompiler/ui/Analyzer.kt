package me.hellrevenger.javadecompiler.ui

import com.strobel.assembler.metadata.JarTypeLoader
import com.strobel.decompiler.DecompilationOptions
import me.hellrevenger.javadecompiler.decompiler.FullScanTextOutput
import me.hellrevenger.javadecompiler.decompiler.NoRetryMetadataSystem
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.ActionEvent
import java.util.jar.JarFile
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import kotlin.concurrent.thread

class Analyzer : JTree() {
    val analyses = hashMapOf<String, FullScanTextOutput.JavaType>()
    val owners = hashMapOf<String, Set<String>>()
    val ownersTiny = hashMapOf<String, Set<String>>()
    val root = DefaultMutableTreeNode()


    val dialog: JDialog
    val pane: JTextPane
    val bar: JProgressBar
    var stop = false
    val stopButton:JButton

    init {
        model = DefaultTreeModel(root)

        dialog = JDialog()
        pane = JTextPane()
        bar = JProgressBar()
        stopButton = JButton(object : AbstractAction("Stop") {
            override fun actionPerformed(e: ActionEvent?) {
                stop = true
            }
        })

        dialog.layout = GridBagLayout()
        val gbc = GridBagConstraints()
        gbc.insets = Insets(5,5,5,5)
        pane.isEditable = false
        val scroll = JScrollPane(pane)
        scroll.preferredSize = Dimension(300, 200)


        gbc.gridx = 0
        gbc.gridy = 0
        dialog.add(bar, gbc)
        gbc.gridx++
        dialog.add(stopButton, gbc)
        gbc.gridx = 0
        gbc.gridy++
        gbc.gridwidth = 2
        dialog.add(scroll, gbc)
        gbc.gridwidth = 1

        dialog.pack()

        Utils.setToCenter(dialog)

        addTreeWillExpandListener(object : TreeWillExpandListener{
            override fun treeWillExpand(event: TreeExpansionEvent) {
                (event.path.lastPathComponent as? LazyNode)?.loadChildren()
            }

            override fun treeWillCollapse(event: TreeExpansionEvent) {

            }
        })
    }

    fun scanJar(path: String, filter: String = "") {
        if(owners.contains(path)) return
        owners[path] = setOf()

        pane.text = ""
        dialog.isVisible = true

        val jar = JarFile(path)
        var counter = 0
        for(entry in jar.entries()) {
            counter++
        }
        bar.maximum = counter
        bar.value = 0

        thread {
            val typeLoader = JarTypeLoader(jar)
            var system = NoRetryMetadataSystem(typeLoader)
            counter = 0
            val opt = DecompilationOptions()
            val settings = MainWindow.settings
            val lang = settings.language
            val textOutput = FullScanTextOutput()
            for(entry in jar.entries()) {
                if(stop) break
                var name = entry.realName
                if(name.endsWith("/"))
                    name = name.substring(0, name.length - 1)
                if(name.startsWith("/"))
                    name = name.substring(1)
                if(!entry.isDirectory && name.endsWith(".class") && (filter == "" || name.startsWith(filter))) {
                    name = name.split(".")[0]
                    if(name in ownersTiny) continue
                    pane.document.insertString(0, "analyzing $name\n", null)
                    if(pane.document.length > 15000) {
                        pane.document.remove(10000, pane.document.length - 10000)
                    }
                    dialog.pack()
                    val ref = system.lookupType(name)
                    val resolve = ref.resolve()
                    lang.decompileType(resolve, textOutput, opt)

                    if(counter++ % 100 == 0) {
                        system = NoRetryMetadataSystem(typeLoader)
                    }
                }
                bar.value++
            }
            addScanResult(path, textOutput.types)
            dialog.isVisible = false
        }
    }

    fun addScanResult(jarPath: String, result: Map<String, FullScanTextOutput.JavaType>) {
        owners[jarPath] = result.keys
        result.forEach { (t, u) ->
            if(t in analyses) return@forEach
            analyses[t] = u
        }
    }

    fun analyze(href: String) {
        val href = if(href.startsWith("!")) href.substring(1) else href

        if("." in href) {
            val split = href.split(".")
            val type = split[0]
            val name = split[1]
            println("type: $type, name: $name")
            if(name.contains(" ")) {
                analyses[type]?.methods?.get(name)?.let {
                    root.add(HasUsageNode(it))
                }
            } else {
                analyses[type]?.fields?.get(name)?.let {
                    root.add(HasUsageNode(it))
                }
            }
        } else {
            analyses[href]?.let {
                root.add(HasUsageNode(it))
            }
        }
        expandRow(0)
    }
}