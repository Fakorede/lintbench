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
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        visitNodeRecursively(context, root)
    }

    private fun visitNodeRecursively(context: XmlContext, node: Node) {
        when (node.nodeType) {
            Node.ELEMENT_NODE -> {
                val element = node as Element
                // Check attributes
                val attrs = element.attributes
                for (i in 0 until attrs.length) {
                    val attr = attrs.item(i) as Attr
                    checkForUnescapedChars(context, attr, attr.value)
                }
                // Visit children
                val children = element.childNodes
                for (i in 0 until children.length) {
                    visitNodeRecursively(context, children.item(i))
                }
            }
            Node.TEXT_NODE -> {
                val text = node.nodeValue ?: return
                checkForUnescapedCharsInText(context, node, text)
            }
            else -> {
                val children = node.childNodes
                for (i in 0 until children.length) {
                    visitNodeRecursively(context, children.item(i))
                }
            }
        }
    }

    private fun checkForUnescapedCharsInText(context: XmlContext, node: Node, text: String) {
        // For text nodes, the XML parser has already unescaped entities.
        // We need to check the raw source to find unescaped characters.
        // Since the DOM gives us already-parsed text, we check for characters
        // that should have been escaped: < and &
        // The '>' character is technically allowed in text but should be escaped
        // We check the raw source via the location mechanism
        
        // Check for characters that are invalid in XML text content
        // In parsed text nodes, '<' and '&' would have caused parse errors,
        // but we can check the source directly
        val rawSource = getRawTextContent(context, node) ?: return
        checkRawSource(context, node, rawSource)
    }

    private fun getRawTextContent(context: XmlContext, node: Node): String? {
        val location = context.getLocation(node)
        val source = context.getContents() ?: return null
        val start = location.start ?: return null
        val end = location.end ?: return null
        val startOffset = start.offset
        val endOffset = end.offset
        if (startOffset < 0 || endOffset < 0 || startOffset >= endOffset || endOffset > source.length) {
            return null
        }
        return source.substring(startOffset, endOffset)
    }

    private fun checkRawSource(context: XmlContext, node: Node, rawSource: String) {
        var i = 0
        while (i < rawSource.length) {
            val c = rawSource[i]
            when (c) {
                '&' -> {
                    val semicolonIndex = rawSource.indexOf(';', i + 1)
                    if (semicolonIndex == -1) {
                        reportIssue(context, node, c)
                        i++
                        continue
                    }
                    val entity = rawSource.substring(i + 1, semicolonIndex)
                    if (!isValidEntity(entity)) {
                        reportIssue(context, node, c)
                    }
                    i = semicolonIndex + 1
                    continue
                }
                '<' -> {
                    reportIssue(context, node, c)
                }
                else -> { /* no-op */ }
            }
            i++
        }
    }

    private fun checkForUnescapedChars(context: XmlContext, node: Node, text: String) {
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
                '"' -> {
                    // In attribute values, double quotes need escaping
                    if (node is Attr) {
                        reportIssue(context, node, c)
                    }
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

    private fun reportIssue(context: XmlContext, node: Node, c: Char) {
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