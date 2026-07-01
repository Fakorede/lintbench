package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class DataBindingDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string contains characters that have special usage in XML, \
                you must escape the characters.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("string", "item")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val text = element.textContent ?: return
        checkForUnescapedChars(context, element, text)
    }

    private fun checkForUnescapedChars(context: XmlContext, node: org.w3c.dom.Node, text: String) {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when (c) {
                '&' -> {
                    val semicolonIndex = text.indexOf(';', i + 1)
                    if (semicolonIndex == -1) {
                        reportIssue(context, node, c)
                        i++
                        continue
                    }
                    val entity = text.substring(i + 1, semicolonIndex)
                    if (!isValidEntity(entity)) {
                        reportIssue(context, node, c)
                    }
                    i = semicolonIndex + 1
                    continue
                }
                '<' -> {
                    reportIssue(context, node, c)
                }
                '>' -> {
                    reportIssue(context, node, c)
                }
                else -> { /* no-op */ }
            }
            i++
        }
    }

    private fun isValidEntity(entity: String): Boolean {
        if (entity.isEmpty()) return false
        if (entity == "amp" || entity == "lt" || entity == "gt" ||
            entity == "apos" || entity == "quot") {
            return true
        }
        if (entity.startsWith("#")) {
            val numPart = entity.substring(1)
            if (numPart.startsWith("x") || numPart.startsWith("X")) {
                val hexPart = numPart.substring(1)
                return hexPart.isNotEmpty() && hexPart.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            }
            return numPart.isNotEmpty() && numPart.all { it.isDigit() }
        }
        return false
    }

    private fun reportIssue(context: XmlContext, node: org.w3c.dom.Node, c: Char) {
        val location = when (node) {
            is Attr -> context.getValueLocation(node)
            is Element -> context.getLocation(node)
            else -> context.getLocation(node)
        }
        context.report(
            ISSUE,
            node,
            location,
            "The character `$c` must be escaped in XML: use `${getEscapeSequence(c)}`"
        )
    }

    private fun getEscapeSequence(c: Char): String {
        return when (c) {
            '<' -> "&lt;"
            '>' -> "&gt;"
            '&' -> "&amp;"
            '"' -> "&quot;"
            '\'' -> "&apos;"
            else -> "&#${c.code};"
        }
    }
}