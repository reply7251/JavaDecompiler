package me.hellrevenger.javadecompiler.ui

import com.strobel.assembler.metadata.JarTypeLoader
import com.strobel.decompiler.DecompilationOptions
import com.strobel.decompiler.languages.BytecodeLanguage
import me.hellrevenger.javadecompiler.decompiler.FullScanTextOutput
import me.hellrevenger.javadecompiler.decompiler.NoRetryMetadataSystem
import me.hellrevenger.javadecompiler.decompiler.UsageScanTextOutput
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
    val analyses = hashMapOf<String, UsageScanTextOutput.JavaType>()
    val alreadyFullScan = hashSetOf<String>()
    val owners = hashMapOf<String, HashSet<String>>()
    val root = DefaultMutableTreeNode()
    val openedAnalyses = hashSetOf<String>()

    val markAsScanJar = hashSetOf<String>()
    val markAsScan = hashMapOf<String, HashSet<String>>()
    var scanning = false
    var autoScanningAll = false

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

    val searchGlobal = SearchGlobal()

    init {
        initNotify()
        initScanDialog()
        initTree()
        addTab("Search", JScrollPane(searchGlobal))
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
        dialog.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE

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

    fun scanJar(path: String, filter: String = "", joined: Boolean = false) {
        if(path in alreadyFullScan) return

        dialog.isVisible = true
        stop = false

        val jar = JarFile(path)
        var counter = 0
        for(entry in jar.entries()) {
            counter++
        }
        bar.maximum = counter
        bar.value = 0

        val callback = {
            val typeLoader = JarTypeLoader(jar)
            var system = NoRetryMetadataSystem(typeLoader)
            counter = 0
            val opt = DecompilationOptions()
            val settings = opt.settings
            val lang = BytecodeLanguage()
            settings.language = lang
            val textOutput = FullScanTextOutput()
            val owner = owners[path]
            for (entry in jar.entries()) {
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
                    if(owner?.contains(name) == true) continue

                    val ref = system.lookupType(name)
                    val resolve = ref.resolve()
                    try {
                        lang.decompileType(resolve, textOutput, opt)
                    } catch (e: Exception) {
                        System.err.println("Error on: $name")
                        e.printStackTrace()
                    }

                    if(counter++ % 100 == 0) {
                        system = NoRetryMetadataSystem(typeLoader)
                        updateScan()
                    }
                }
                bar.value++
            }
            if(counter > 0) {
                updateScan()
            }
            addScanResult(path, textOutput.types)
            dialog.isVisible = false
            if(filter == "" && !stop) {
                alreadyFullScan.add(path)
            }
        }
        if (joined) {
            callback()
        } else {
            thread {
                callback()
            }
        }
    }

    fun updateScan() {
        root.children().asIterator().forEach {
            (it as? HasUsageNode)?.reload()
        }
        searchGlobal.search()
    }

    fun addScanResult(jarPath: String, result: Map<String, UsageScanTextOutput.JavaType>) {
        owners.computeIfAbsent(jarPath) { hashSetOf() }.addAll(result.keys)
    }

    fun hasMarkScan(path: String, filter: String) =
        if(filter == "") {
            markAsScanJar.contains(path)
        } else {
            markAsScan[path]?.contains(filter) == true
        }

    fun markScan(path: String, filter: String) {
        if(filter == "") {
            synchronized(markAsScanJar) {
                markAsScanJar.add(path)
            }
        } else {
            synchronized(markAsScan) {
                markAsScan.computeIfAbsent(path) { hashSetOf() }.add(filter)
            }
        }
    }

    fun unmarkScan(path: String, filter: String) {
        if(filter == "") {
            synchronized(markAsScanJar) {
                markAsScanJar.remove(path)
            }
        } else {
            synchronized(markAsScan) {
                markAsScan[path]?.remove(filter)
            }
        }
    }

    fun markScanForType(type: String) {
        val index = type.lastIndexOf("/")
        if(index == -1) return
        val name = type.substring(0, index)
        MainWindow.fileTree.filesNode.models.forEach {  (_, node) ->
            (node as? JarNode)?.let {
                markScan(it.getPath(), name)
            }
        }
    }

    fun scan() {
        synchronized(this) {
            if(scanning) return
            scanning = true

            thread {
                while (!stop && (markAsScan.isNotEmpty() || markAsScanJar.isNotEmpty())) {
                    if(markAsScan.isNotEmpty()) {
                        val toScan = mutableListOf<Pair<String, List<String>>>()
                        synchronized(markAsScan) {
                            markAsScan.entries.forEach { (jar, filters) ->
                                toScan.add(jar to filters.toList())
                            }
                            markAsScan.clear()
                        }
                        toScan.forEach { (jar, filters) ->
                            filters.forEach { filter ->
                                scanJar(jar, filter, joined = true)
                            }
                        }
                    } else {
                        val toScan = mutableListOf<String>()
                        synchronized(markAsScanJar) {
                            toScan.addAll(markAsScanJar)
                            markAsScanJar.clear()
                        }
                        toScan.forEach {
                            scanJar(it, joined = true)
                        }
                    }
                }
                autoScanAll()
                scanning = false
            }
        }
    }

    fun autoScanAll() {
        if(autoScanningAll) return
        autoScanningAll = true
        MainWindow.fileTree.filesNode.models.any {  (_, node) ->
            if(stop) return@any true
            (node as? JarNode)?.let {
                scanJar(it.getPath(), joined = true)
            }
            false
        }
        autoScanningAll = false
    }

    fun analyze(href: String) {
        val href = if(href.startsWith("!")) href.substring(1) else href
        val m = (tree.model as? DefaultTreeModel) ?: return
        if(href in openedAnalyses) return
        if("." in href) {
            val split = href.split(".")
            val type = split[0]
            val name = split[1]

            markScanForType(type)

            if(name.contains(" ")) {
                analyses[type]?.methods
            } else {
                analyses[type]?.fields
            }?.get(name)
        } else {
            markScanForType(href)

            analyses[href]
        }?.let {
            root.add(HasUsageNode(it))
            m.nodeStructureChanged(root)
            openedAnalyses.add(href)
        }
        scan()
        if(!tree.isExpanded(0))
            tree.expandRow(0)

        selectedIndex = indexOfTab("Analyze")
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
                search()
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
            search()
        }
    }

    fun search() {
        val text = inputSearch.text
        if(text.isEmpty()) return
        val model = list.model as? DefaultListModel ?: return
        model.clear()
        val used = hashSetOf<String>()
        val results = mutableListOf<Link>()

        synchronized(MainWindow.analyzer) {
            MainWindow.analyzer.scan()
        }

        MainWindow.analyzer.analyses.forEach { (t, u) ->
            val searchType = comboBox.selectedItem?.toString() ?: return@forEach
            if(searchType.contains("Type") && t.contains(text, true) && u.uses.isNotEmpty()) {
                model.add(model.size, Link(t, "!$t"))
            }
            if(searchType.contains("Method")) {
                u.methods.forEach { (t, u) ->
                    if(t.contains(text, true) && u.uses.isNotEmpty()) {
                        val display = u.toString().split(" ")[0] + "()"
                        results.add(Link(display, "!$u"))
                        used.add(display)
                    }
                }
            }
            if(searchType.contains("Field")) {
                u.fields.forEach { (t, u) ->
                    if(t.contains(text, true) && u.uses.isNotEmpty()) {
                        val display = u.toString()
                        results.add(Link(display, "!$u"))
                        used.add(display)
                    }
                }
            }
            if(searchType.contains(SEARCH_USED)) {
                u.fields.forEach { (t, u) ->
                    val display = u.toString()
                    if(t.contains(text, true) && !used.contains(display)) {
                        results.add(Link(display, "!$u"))
                    }
                }
                u.methods.forEach { (t, u) ->
                    val display = u.toString().split(" ")[0] + "()"
                    if(t.contains(text, true) && !used.contains(display)) {
                        results.add(Link(display, "!$u"))
                    }
                }
            }
        }
        model.addAll(model.size(), results.sortedBy { it.toString() })
    }

    class Link(val display: String, val description: String) {
        override fun toString() = display
    }
}