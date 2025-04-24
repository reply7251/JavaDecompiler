package me.hellrevenger.javadecompiler.ui

import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JTextField

class JSearch : JDialog() {
    var target: Searchable? = null
    var config = SearchConfig.DEFAULT

    init {
        layout = GridBagLayout()

        val inputSearch = JTextField()
        val searchButton = JButton(object : AbstractAction("Search") {
            override fun actionPerformed(e: ActionEvent) {
                if(inputSearch.text.isNotEmpty())
                    target?.search(inputSearch.text, config)
            }
        })
        val matchCaseCheckBox = JCheckBox("Match case ")
        val regexCheckBox = JCheckBox("Regex ")
        val dotCheckBox = JCheckBox("Dot match all")
        val searchConfigActionListener = { e: ActionEvent? ->
            dotCheckBox.isEnabled = regexCheckBox.isSelected
            config = SearchConfig(matchCaseCheckBox.isSelected, regexCheckBox.isSelected, dotCheckBox.isSelected && dotCheckBox.isEnabled)
        }

        matchCaseCheckBox.addActionListener(searchConfigActionListener)
        regexCheckBox.addActionListener(searchConfigActionListener)
        dotCheckBox.addActionListener(searchConfigActionListener)

        val gbc = GridBagConstraints()
        gbc.insets = Insets(5,5,5,5)

        gbc.gridx = 0
        gbc.gridy = 0
        add(JLabel("Search: "), gbc)
        gbc.gridx = 1
        gbc.gridwidth = 2
        add(inputSearch, gbc)
        gbc.gridx = 3
        gbc.gridwidth = 1
        add(searchButton, gbc)
        gbc.gridx = 0
        gbc.gridy++
        add(matchCaseCheckBox, gbc)
        gbc.gridx++
        add(regexCheckBox, gbc)
        gbc.gridx++
        add(dotCheckBox, gbc)

        rootPane.defaultButton = searchButton

        searchConfigActionListener.invoke(null)

        inputSearch.preferredSize = Dimension(200, 20)
        pack()

        Utils.setToCenter(this)
    }
}

interface Searchable {
    fun search(text: String, config: SearchConfig = SearchConfig.DEFAULT)
}

class SearchConfig(
    val matchCase: Boolean = false,
    val regex: Boolean = false,
    val dotMatchAll: Boolean = true
) {
    companion object {
        val DEFAULT = SearchConfig()
    }
}