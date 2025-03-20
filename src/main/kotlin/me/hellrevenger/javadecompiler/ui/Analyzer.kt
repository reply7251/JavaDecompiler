package me.hellrevenger.javadecompiler.ui

import com.strobel.assembler.metadata.JarTypeLoader
import com.strobel.decompiler.DecompilationOptions
import com.strobel.decompiler.languages.BytecodeLanguage
import me.hellrevenger.javadecompiler.decompiler.FullScanTextOutput
import me.hellrevenger.javadecompiler.decompiler.NoRetryMetadataSystem
import java.awt.*
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

    val tree: JTree = JTree()
    val dialog: JDialog = JDialog()
    val pane: JTextPane = JTextPane()
    val bar: JProgressBar = JProgressBar()
    var stop = false
    val stopButton: JButton = JButton(object : AbstractAction("Stop") {
        override fun actionPerformed(e: ActionEvent?) {
            stop = true
        }
    })
    val notify = JDialog()
    val notifyLabel = JTextArea()
    val NOT_SCANNED = "You haven't scan for this node:\n"

    init {
        initNotify()
        initScanDialog()
        initTree()
        addTab("Search", JScrollPane(SearchGlobal()))
    }

    fun initNotify() {
        notifyLabel.isEnabled = false
        notifyLabel.text = NOT_SCANNED
        notifyLabel.border = BorderFactory.createEmptyBorder(10,10,10,10)
        notify.add(notifyLabel)
        notify.pack()
        notify.addFocusListener(object : FocusListener {
            override fun focusGained(e: FocusEvent?) { }
            override fun focusLost(e: FocusEvent?) {
                notify.isVisible = false
            }
        })
        Utils.setToCenter(notify)
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
                        val path = getClosestPathForLocation(e.x, e.y) ?: return
                        (path.lastPathComponent as? HasUsageNode)?.let {
                            val description = it.target.toString()
                            val delimiter = description.indexOf(".")
                            val className = if(delimiter == -1) description else description.substring(0, delimiter)
                            waitCursor {
                                MainWindow.fileTree.openClass(className)?.let { pane ->
                                    val node = (it.parent?.parent as? HasUsageNode) ?: it
                                    node.target.toString().let {  target ->
                                        val from = "!$description"
                                        if(node == it) {
                                            MainWindow.sourceViewer.searchHref(from)
                                        } else {
                                            MainWindow.sourceViewer.searchHref(target, from)
                                        }
                                    }
                                }
                            }
                        }
                    } else if(e.button == 3) {
                        val path = getClosestPathForLocation(e.x, e.y) ?: return
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
        val m = (tree.model as? DefaultTreeModel) ?: return
        if(href in openedAnalyses) return
        var success = false
        if("." in href) {
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
        }?.let {
            root.add(HasUsageNode(it))
            m.nodeStructureChanged(root)
            success = true
        }
        if(success) {
            openedAnalyses.add(href)
        } else {
            notifyLabel.text = NOT_SCANNED + href
            notify.pack()
            notify.isVisible = true
        }
        tree.expandRow(0)
    }
}

class SearchGlobal : JPanel() {
    val list = JList<Link>()
    val comboBox = JComboBox<String>()
    val inputSearch = JTextField()

    val SEARCH_USED = "Used"

    init {
        layout = GridBagLayout()
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.BOTH
        gbc.insets = Insets(5,5,5,5)

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
                if(comboBox.selectedItem?.toString() == SEARCH_USED) {
                    MainWindow.analyzer.analyze(href)
                    MainWindow.analyzer.selectedIndex = MainWindow.analyzer.indexOfTab("Analyze")
                } else {
                    MainWindow.fileTree.openClass(className)?.let { pane ->
                        MainWindow.sourceViewer.searchHref(href)
                    }
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
        comboBox.addItem(SEARCH_USED)

        comboBox.addActionListener {
            search(inputSearch.text)
        }
    }

    fun search(text: String) {
        if(text.isEmpty()) return
        val model = list.model as? DefaultListModel ?: return
        model.clear()
        val used = hashSetOf<String>()
        MainWindow.analyzer.analyses.forEach { (t, u) ->
            val searchType = comboBox.selectedItem?.toString() ?: return@forEach
            if(searchType.contains("Type") && t.contains(text, true) && u.uses.isNotEmpty()) {
                model.add(model.size, Link(t, "!$t"))
            }
            if(searchType.contains("Method")) {
                u.methods.forEach { (t, u) ->
                    if(t.contains(text, true) && u.uses.isNotEmpty()) {
                        val display = u.toString().split(" ")[0] + "()"
                        model.add(model.size, Link(display, "!$u"))
                        used.add(display)
                    }
                }
            }
            if(searchType.contains("Field")) {
                u.fields.forEach { (t, u) ->
                    if(t.contains(text, true) && u.uses.isNotEmpty()) {
                        val display = u.toString()
                        model.add(model.size, Link(display, "!$u"))
                        used.add(display)
                    }
                }
            }
            if(searchType.contains(SEARCH_USED)) {
                u.fields.forEach { (t, u) ->
                    val display = u.toString()
                    if(t.contains(text, true) && !used.contains(display)) {
                        model.add(model.size, Link(display, "!$u"))
                    }
                }
                u.methods.forEach { (t, u) ->
                    val display = u.toString().split(" ")[0] + "()"
                    if(t.contains(text, true) && !used.contains(display)) {
                        model.add(model.size, Link(display, "!$u"))
                    }
                }
            }
        }
    }

    class Link(val display: String, val description: String) {
        override fun toString() = display
    }
}