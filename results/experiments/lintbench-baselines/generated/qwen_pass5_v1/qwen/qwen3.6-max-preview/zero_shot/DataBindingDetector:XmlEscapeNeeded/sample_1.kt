package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class DataBindingDetector : ResourceXmlDetector() {
    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = "When a string contains characters that have special usage in XML, you must escape the characters.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(DataBindingDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun getApplicableElements(): Collection<String>? = ALL
    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val value = attr.nodeValue ?: continue
            checkValue(context, attr, value, isAttribute = true)
        }

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.TEXT_NODE) {
                val text = child.nodeValue ?: continue
                checkValue(context, child, text, isAttribute = false)
            }
        }
    }

    private fun checkValue(context: XmlContext, node: Node, value: String, isAttribute: Boolean) {
        if (value.contains("@{") || value.contains("@={")) return

        var i = 0
        while (i < value.length) {
            val c = value[i]
            val replacement = when (c) {
                '&' -> if (!isValidEntity(value, i)) "&amp;" else null
                '<' -> "&lt;"
                '>' -> if (!isAttribute) "&gt;" else null
                '"' -> if (isAttribute) "&quot;" else null
                '\'' -> if (isAttribute) "&apos;" else null
                else -> null
            }

            if (replacement != null) {
                val location = context.getLocation(node)
                context.report(
                    ISSUE,
                    location,
                    "Replace `$c` with `$replacement` to escape XML special character"
                )
                return
            }
            i++
        }
    }

    private fun isValidEntity(value: String, start: Int): Boolean {
        if (start + 1 >= value.length) return false
        val next = value[start + 1]
        if (next == '#') return true
        val entities = listOf("amp;", "lt;", "gt;", "quot;", "apos;")
        return entities.any { value.startsWith(it, start + 1) }
    }
}