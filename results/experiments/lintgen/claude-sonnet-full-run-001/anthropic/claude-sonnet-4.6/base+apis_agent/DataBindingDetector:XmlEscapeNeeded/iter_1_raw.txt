package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.Text

class DataBindingDetector : Detector(), XmlScanner {

    companion object {
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

        private val SPECIAL_CHARS = setOf('<', '>', '&')
        private val SPECIAL_ATTR_CHARS = setOf('<', '&')
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return true
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        checkNode(context, document)
    }

    private fun checkNode(context: XmlContext, node: Node) {
        when (node.nodeType) {
            Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                val text = node.nodeValue ?: return
                checkTextForSpecialChars(context, node, text, isAttribute = false)
            }
            Node.ELEMENT_NODE -> {
                val element = node as Element
                // Check attributes
                val attrs = element.attributes
                for (i in 0 until attrs.length) {
                    val attr = attrs.item(i) as Attr
                    val value = attr.value ?: continue
                    checkTextForSpecialChars(context, attr, value, isAttribute = true)
                }
                // Check children
                val children = element.childNodes
                for (i in 0 until children.length) {
                    checkNode(context, children.item(i))
                }
            }
            Node.DOCUMENT_NODE -> {
                val children = node.childNodes
                for (i in 0 until children.length) {
                    checkNode(context, children.item(i))
                }
            }
        }
    }

    private fun checkTextForSpecialChars(
        context: XmlContext,
        node: Node,
        text: String,
        isAttribute: Boolean
    ) {
        val specialChars = if (isAttribute) SPECIAL_ATTR_CHARS else SPECIAL_CHARS

        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c in specialChars) {
                if (c == '&') {
                    // Check if this is a valid XML entity reference
                    val semicolonIndex = text.indexOf(';', i)
                    if (semicolonIndex != -1) {
                        val entity = text.substring(i, semicolonIndex + 1)
                        if (isValidXmlEntity(entity)) {
                            i = semicolonIndex + 1
                            continue
                        }
                    }
                }

                val location = if (node is Attr) {
                    context.getValueLocation(node)
                } else {
                    context.getLocation(node)
                }

                val charDescription = when (c) {
                    '<' -> "'<' (less-than)"
                    '>' -> "'>' (greater-than)"
                    '&' -> "'&' (ampersand)"
                    else -> "'$c'"
                }

                val escapedChar = when (c) {
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '&' -> "&amp;"
                    else -> c.toString()
                }

                context.report(
                    ISSUE,
                    node,
                    location,
                    "The character $charDescription must be escaped as `$escapedChar`"
                )
                return
            }
            i++
        }
    }

    private fun isValidXmlEntity(entity: String): Boolean {
        if (!entity.startsWith("&") || !entity.endsWith(";")) return false
        val name = entity.substring(1, entity.length - 1)
        if (name.isEmpty()) return false

        // Named entities
        if (name in setOf("amp", "lt", "gt", "apos", "quot")) return true

        // Numeric character references
        if (name.startsWith("#")) {
            val numStr = name.substring(1)
            return if (numStr.startsWith("x") || numStr.startsWith("X")) {
                numStr.substring(1).all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            } else {
                numStr.all { it.isDigit() }
            }
        }

        return false
    }
}