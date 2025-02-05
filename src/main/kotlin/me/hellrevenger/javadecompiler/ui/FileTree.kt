package me.hellrevenger.javadecompiler.ui

import java.awt.Color
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel

val SUPPORT_FILES = arrayOf("jar", "class")

class FileTree() : JTree() {
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
        if(file.extension.lowercase() == "jar")
            jars.addJar(file)
        expandRow(0)
    }
}