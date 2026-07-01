package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

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

        private val VALID_NAMED_ENTITIES = setOf("amp", "lt", "gt", "apos", "quot")
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        checkNodeRecursively(context, root)
    }

    private fun checkNodeRecursively(context: XmlContext, node: Node) {
        when (node.nodeType) {
            Node.ELEMENT_NODE -> {
                val element = node as Element
                val attrs = element.attributes
                for (i in 0 until attrs.length) {
                    val attr = attrs.item(i) as Attr
                    checkAttributeRawValue(context, attr)
                }
                val children = element.childNodes
                for (i in 0 until children.length) {
                    checkNodeRecursively(context, children.item(i))
                }
            }
            Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                checkTextNodeRawValue(context, node)
            }
            else -> {
                val children = node.childNodes
                for (i in 0 until children.length) {
                    checkNodeRecursively(context, children.item(i))
                }
            }
        }
    }

    private fun checkTextNodeRawValue(context: XmlContext, node: Node) {
        val source = context.getContents() ?: return
        val location = context.getLocation(node)
        val start = location.start ?: return
        val end = location.end ?: return
        val startOffset = start.offset
        val endOffset = end.offset
        if (startOffset < 0 || endOffset <= startOffset || endOffset > source.length) return

        val rawText = source.substring(startOffset, endOffset)
        checkRawTextForUnescapedChars(context, node, rawText, startOffset)
    }

    private fun checkAttributeRawValue(context: XmlContext, attr: Attr) {
        val source = context.getContents() ?: return
        val location = context.getValueLocation(attr)
        val start = location.start ?: return
        val end = location.end ?: return
        val startOffset = start.offset
        val endOffset = end.offset
        if (startOffset < 0 || endOffset <= startOffset || endOffset > source.length) return

        // The value location includes the quotes, so skip them
        val rawValue = source.substring(startOffset, endOffset)
        // Strip surrounding quotes if present
        val (content, contentStart) = if (rawValue.isNotEmpty() &&
            (rawValue[0] == '"' || rawValue[0] == '\'')) {
            val stripped = if (rawValue.length >= 2) rawValue.substring(1, rawValue.length - 1) else ""
            Pair(stripped, startOffset + 1)
        } else {
            Pair(rawValue, startOffset)
        }

        checkRawTextForUnescapedChars(context, attr, content, contentStart)
    }

    private fun checkRawTextForUnescapedChars(context: XmlContext, node: Node, rawText: String, baseOffset: Int) {
        var i = 0
        while (i < rawText.length) {
            val c = rawText[i]
            when (c) {
                '&' -> {
                    // Check if it's a valid entity reference
                    val semicolonIndex = rawText.indexOf(';', i + 1)
                    if (semicolonIndex == -1) {
                        // No semicolon found - unescaped &
                        reportIssueAtOffset(context, node, c, baseOffset + i)
                        i++
                        continue
                    }
                    val entity = rawText.substring(i + 1, semicolonIndex)
                    if (!isValidEntity(entity)) {
                        reportIssueAtOffset(context, node, c, baseOffset + i)
                    }
                    i = semicolonIndex + 1
                    continue
                }
                '<' -> {
                    reportIssueAtOffset(context, node, c, baseOffset + i)
                }
                else -> { /* ok */ }
            }
            i++
        }
    }

    private fun isValidEntity(entity: String): Boolean {
        if (entity.isEmpty()) return false
        if (entity in VALID_NAMED_ENTITIES) return true
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

    private fun reportIssueAtOffset(context: XmlContext, node: Node, c: Char, offset: Int) {
        val location = when (node) {
            is Attr -> context.getValueLocation(node)
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