package me.hellrevenger.javadecompiler.ui

import me.hellrevenger.javadecompiler.decompiler.FullScanTextOutput
import java.io.File
import java.util.jar.JarFile
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.MutableTreeNode

fun <K: Comparable<K>,V> Map<K,V>.sortByKey() = this.toList().sortedBy { it.first }.map { it.second }

abstract class Node(val node: MutableTreeNode) {
    abstract fun getChildren(): List<Node>
}

open class ClassNode(val name: String, node: MutableTreeNode) : Node(node) {
    override fun getChildren(): List<Node> {
        return emptyList()
    }

    override fun toString() = name
}

class ClassFileNode(val file: File, node: MutableTreeNode) : ClassNode(file.name, node)

open class PackageNode(val name: String, node: MutableTreeNode) : Node(node) {
    val packages = hashMapOf<String, PackageNode>()
    val classes = hashMapOf<String, ClassNode>()
    val files = hashMapOf<String, Node>()

    override fun getChildren(): List<Node> {
        return packages.sortByKey() + classes.sortByKey() + files.sortByKey()
    }

    fun getPackage(path: String): PackageNode {
        val delimiter = path.indexOf("/")
        if(delimiter != -1) {
            val packageName = path.substring(0, delimiter)
            getChildren().find {
                (it as? PackageNode)?.let {
                    if(it.name == packageName) {
                        return@find true
                    }
                }
                return@find false
            }?.let {
                return (it as PackageNode).getPackage(path.substring(delimiter+1))
            }
        }
        packages[path]?.let {
            return it
        }
        val packageNode = DefaultMutableTreeNode()
        val thePackage = PackageNode(path, packageNode)
        packageNode.userObject = thePackage
        packages[path] = thePackage
        node.insert(packageNode, getChildren().indexOf(thePackage))
        return thePackage
    }

    fun getClass(path: String): ClassNode {
        val delimiter = path.lastIndexOf("/")
        if(delimiter != -1) {
            return getPackage(path.substring(0, delimiter)).getClass(path.substring(delimiter+1))
        }
        classes[path]?.let {
            return it
        }
        val classNode = DefaultMutableTreeNode()
        val theClass = ClassNode(path.split(".")[0], classNode)
        classNode.userObject = theClass
        classes[path] = theClass
        node.insert(classNode, getChildren().indexOf(theClass))
        return theClass
    }

    override fun toString() = name
}

class JarNode(val file: File, node: MutableTreeNode) : PackageNode("", node) {
    init {
        val jar = JarFile(file)
        jar.entries().asIterator().forEach {
            var name = it.realName
            if(name.endsWith("/"))
                name = name.substring(0, name.length - 1)
            if(name.startsWith("/"))
                name = name.substring(1)
            if(it.isDirectory) getPackage(name)
            else if(name.endsWith(".class")) {
                getClass(name)
            }
        }
    }

    fun getPath() = file.absolutePath

    override fun toString() = file.name
}

class FilesNode : DefaultMutableTreeNode() {
    val models = mutableMapOf<String, Node>()
    fun getChildren() = models.values.toList()

    fun addJar(file: File): Boolean {
        if(file.absolutePath in models) return false
        val jarNode = DefaultMutableTreeNode()
        val jar = JarNode(file, jarNode)
        jarNode.userObject = jar
        models[file.absolutePath] = jar

        insert(jarNode, 0)
        return true
    }

    fun removeFile(file: String) {
        val jarOrClass = models.remove(file) ?: return
        remove(jarOrClass.node)
    }

    fun addClass(file: File): Boolean {
        if(file.absolutePath in models) return false
        val classNode = DefaultMutableTreeNode()
        val theClass = ClassFileNode(file, classNode)
        classNode.userObject = theClass
        models[file.absolutePath] = theClass
        insert(classNode, 0)
        return true
    }

    override fun toString(): String {
        return ""
    }
}

class HasUsageNode(val target: FullScanTextOutput.HasUsage) : DefaultMutableTreeNode(target) {
    init {
        val uses = LazyNode({ node ->
            target.uses.forEach {
                node.add(HasUsageNode(it))
            }
        }, "uses")
        val usedBy = LazyNode({ node ->
            target.usedBy.forEach {
                node.add(HasUsageNode(it))
            }
        }, "used by")
        insert(uses, childCount)
        insert(usedBy, childCount)
    }

    override fun toString(): String {
        val result = super.toString()
        if(" " in result) return result.split(" ")[0] + "()"
        return result
    }
}

class LazyNode(val onLoad: (LazyNode) -> Unit, obj: Any? = null) : DefaultMutableTreeNode(obj) {
    var loaded = false

    fun loadChildren() {
        if(loaded) return
        loaded = true
        onLoad(this)
    }

    override fun isLeaf() = false
}