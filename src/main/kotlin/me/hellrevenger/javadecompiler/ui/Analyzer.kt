package me.hellrevenger.javadecompiler.ui

import com.strobel.assembler.metadata.JarTypeLoader
import com.strobel.decompiler.DecompilationOptions
import com.strobel.decompiler.languages.BytecodeLanguage
import me.hellrevenger.javadecompiler.decompiler.FullScanTextOutput
import me.hellrevenger.javadecompiler.decompiler.NoRetryMetadataSystem
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.*
import java.util.jar.JarFile
import javax.swing.*
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import kotlin.concurrent.thread

class Analyzer : JTabbedPane() {
    val analyses = hashMapOf<String, FullScanTextOutput.JavaType>()
    val alreadyFullScan = hashSetOf<String>()
    val owners = hashMapOf<String, HashSet<String>>()
    val root = DefaultMutableTreeNode()
    val openedAnalyses = hashSetOf<String>()

    val tree: JTree
    val dialog: JDialog
    val pane: JTextPane
    val bar: JProgressBar
    var stop = false
    val stopButton:JButton

    init {
        tree = JTree()

        dialog = JDialog()
        pane = JTextPane()
        bar = JProgressBar()
        stopButton = JButton(object : AbstractAction("Stop") {
            override fun actionPerformed(e: ActionEvent?) {
                stop = true
            }
        })

        initScanDialog()
        initTree()
        addTab("Search", JScrollPane(SearchGlobal()))
    }

    fun initScanDialog() {
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
        dialog.isAlwaysOnTop = true

        Utils.setToCenter(dialog)
    }

    fun initTree() {
        with(tree) {
            model = DefaultTreeModel(root)
            setToggleClickCount(-1)

            addTreeWillExpandListener(object : TreeWillExpandListener{
                override fun treeWillExpand(event: TreeExpansionEvent) {
                    (event.path.lastPathComponent as? LazyNode)?.loadChildren()
                }

                override fun treeWillCollapse(event: TreeExpansionEvent) { }
            })

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if(e.clickCount == 2) {
                        val path = getPathForLocation(e.x, e.y) ?: return
                        (path.lastPathComponent as? HasUsageNode)?.let {
                            val description = it.target.toString()
                            val delimiter = description.indexOf(".")
                            val className = if(delimiter == -1) description else description.substring(0, delimiter)
                            MainWindow.fileTree.openClass(className)?.let { pane ->
                                (it.parent?.parent as? HasUsageNode)?.target?.toString()?.let {  target ->
                                    val from = "!$description"
                                    MainWindow.sourceViewer.searchHref(target, from)
                                }
                            }
                        }
                    } else if(e.button == 3) {
                        val path = getPathForLocation(e.x, e.y) ?: return
                        if(path.pathCount != 2) return
                        val node = (path.lastPathComponent as? HasUsageNode) ?: return
                        val menu = JPopupMenu()
                        menu.invoker = tree
                        menu.setLocation(e.xOnScreen, e.yOnScreen)
                        menu.add(object : AbstractAction("Remove") {
                            override fun actionPerformed(e: ActionEvent) {
                                openedAnalyses.remove(node.target.toString())
                                node.removeFromParent()
                                (tree.model as? DefaultTreeModel)?.nodeStructureChanged(root)
                            }
                        })
                        menu.isVisible = true
                    }
                }
            })
        }
        add("Analyze", JScrollPane(tree))
    }

    fun scanJar(path: String, filter: String = "") {
        if(path in alreadyFullScan) return

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
            val settings = opt.settings
            val lang = BytecodeLanguage()
            settings.language = lang
            val textOutput = FullScanTextOutput()
            val owner = owners[path]
            for(entry in jar.entries()) {
                if(stop) break
                var name = entry.realName
                if(name.endsWith("/"))
                    name = name.substring(0, name.length - 1)
                if(name.startsWith("/"))
                    name = name.substring(1)
                if(!entry.isDirectory && name.endsWith(".class") && (filter == "" || name.startsWith(filter))) {
                    name = name.split(".")[0]
                    pane.document.insertString(0, "analyzing $name\n", null)
                    if(pane.document.length > 15000) {
                        pane.document.remove(10000, pane.document.length - 10000)
                    }
                    dialog.pack()
                    if(owner?.contains(path) == true) continue

                    val ref = system.lookupType(name)
                    val resolve = ref.resolve()
                    try {
                        lang.decompileType(resolve, textOutput, opt)
                    } catch (e: Exception) {
                        println("Error on: $name")
                        e.printStackTrace()
                    }

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
        owners.computeIfAbsent(jarPath) { hashSetOf() }.addAll(result.keys)
        result.forEach { (t, u) ->
            if(t in analyses) return@forEach
            analyses[t] = u
        }
    }

    fun analyze(href: String) {
        val href = if(href.startsWith("!")) href.substring(1) else href
        if(href in openedAnalyses) return
        openedAnalyses.add(href)
        val node = if("." in href) {
            val split = href.split(".")
            val type = split[0]
            val name = split[1]
            if(name.contains(" ")) {
                analyses[type]?.methods
            } else {
                analyses[type]?.fields
            }?.get(name)
        } else {
            analyses[href]
        }?.let { HasUsageNode(it) }
        node?.let {
            val m = (tree.model as? DefaultTreeModel) ?: return@let
            root.add(it)
            m.nodeStructureChanged(root)
        }
        tree.expandRow(0)
    }
}

class SearchGlobal : JPanel() {
    val list = JList<Link>()
    val comboBox = JComboBox<String>()

    init {
        layout = GridBagLayout()
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.BOTH
        gbc.insets = Insets(5,5,5,5)

        val inputSearch = JTextField()
        initSearchInput(inputSearch)
        initList()
        initComboBox()

        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 1.0
        add(inputSearch, gbc)
        gbc.weightx = 0.0
        gbc.gridx++
        add(comboBox, gbc)
        gbc.gridx = 0
        gbc.gridy++
        gbc.gridwidth = 2
        gbc.weighty = 1.0
        add(JScrollPane(list), gbc)
        gbc.weighty = 0.0
        gbc.gridwidth = 1
    }

    fun initSearchInput(input: JTextField) {
        input.action = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                search(input.text)
            }
        }
    }

    fun initList() {
        val model = DefaultListModel<Link>()
        list.model = model
        list.layoutOrientation = JList.VERTICAL

        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if(e.clickCount != 2) return
                val href = list.selectedValue.description
                val description = if(href.startsWith("!")) href.substring(1) else href
                val delimiter = description.indexOf(".")
                val className = if(delimiter == -1) description else description.substring(0, delimiter)
                MainWindow.fileTree.openClass(className)?.let { pane ->
                    MainWindow.sourceViewer.searchHref(href)
                }
            }
        })
        list.visibleRowCount = -1
    }

    fun initComboBox() {
        comboBox.addItem("Type/Method/Field")
        comboBox.addItem("Type")
        comboBox.addItem("Method/Field")
        comboBox.addItem("Method")
        comboBox.addItem("Field")
    }

    fun search(text: String) {
        val model = list.model as? DefaultListModel ?: return
        model.clear()
        MainWindow.analyzer.analyses.forEach { (t, u) ->
            val searchType = comboBox.selectedItem?.toString() ?: return@forEach
            if(searchType.contains("Type") && t.contains(text, true)) {
                model.add(model.size, Link(t, "!$t"))
            }
            if(searchType.contains("Method")) {
                u.methods.forEach { (t, u) ->
                    if(t.contains(text, true))
                        model.add(model.size, Link(u.toString().split(" ")[0] + "()", "!$u"))
                }
            }
            if(searchType.contains("Field")) {
                u.fields.forEach { (t, u) ->
                    if(t.contains(text, true))
                        model.add(model.size, Link(u.toString(), "!$u"))
                }
            }
        }
    }

    class Link(val display: String, val description: String) {
        override fun toString() = display
    }
}