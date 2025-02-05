package me.hellrevenger.javadecompiler.ui

import java.io.File
import java.util.*
import java.util.jar.JarFile
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode

fun <K: Comparable<K>,V> Map<K,V>.sortByKey() = this.toList().sortedBy { it.first }.map { it.second }

abstract class Node(var model: DefaultTreeModel? = null, @get:JvmName("getParentKt") val parent: Node? = null) : TreeNode {
    abstract fun getChildren(): List<Node>

    override fun getChildAt(childIndex: Int) = getChildren()[childIndex]

    override fun getChildCount() = getChildren().size

    override fun getParent() = parent

    override fun getIndex(node: TreeNode?) = getChildren().indexOf(node)

    override fun isLeaf() = childCount == 0

    override fun children() = Collections.enumeration(getChildren())
}

class ClassNode(model: DefaultTreeModel, val name: String) : Node(model) {
    init {

    }

    override fun getChildren(): List<Node> {
        return emptyList()
    }

    override fun getAllowsChildren() = false

    override fun toString() = name
}

open class PackageNode(model: DefaultTreeModel, val name: String, parent: JarNode? = null) : Node(model, parent) {
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
        val thePackage = PackageNode(model!!, path, this as? JarNode ?: parent as? JarNode)
        packages[path] = thePackage
        return thePackage
    }

    fun getClass(path: String): ClassNode {
        val delimiter = path.lastIndexOf("/")
        if(delimiter != -1) {
            return getPackage(path.substring(0, delimiter)).getClass(path.substring(delimiter+1))
        }
        val theClass = ClassNode(model!!, path.split(".")[0])
        classes[path] = theClass
        return theClass
    }

    override fun toString() = name
    override fun getAllowsChildren() = true
}

class JarNode(model: DefaultTreeModel, val file: File) : PackageNode(model, "") {
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

    override fun toString() = file.absolutePath
}

class Jars : Node() {
    val models = mutableMapOf<String, Node>()
    override fun getChildren() = models.values.toList()

    fun addJar(file: File) {
        val jar = JarNode(model!!, file)
        models[file.absolutePath] = jar
        model!!.nodeChanged(jar)
    }

    override fun getAllowsChildren() = true

    override fun toString(): String {
        return ""
    }
}