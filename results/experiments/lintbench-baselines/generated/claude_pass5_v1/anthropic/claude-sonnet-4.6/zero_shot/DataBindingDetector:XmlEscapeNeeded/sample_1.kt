package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.Text
import java.util.EnumSet

class DataBindingDetector : Detector(), Detector.XmlScanner {

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string contains characters that have special usage in XML, \
                you must escape the characters.

                For example, `&` must be escaped as `&amp;`, `<` must be escaped as `&lt;`, \
                `>` must be escaped as `&gt;`, `"` must be escaped as `&quot;` (in attribute values), \
                and `'` must be escaped as `&apos;` (in attribute values delimited by single quotes).
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                EnumSet.of(Scope.RESOURCE_FILE)
            )
        )

        // Characters that need escaping in XML text content
        private val XML_TEXT_SPECIAL_CHARS = setOf('<', '&')

        // Characters that need escaping in XML attribute values (double-quoted)
        private val XML_ATTR_SPECIAL_CHARS_DOUBLE_QUOTE = setOf('<', '&', '"')

        // Characters that need escaping in XML attribute values (single-quoted)
        private val XML_ATTR_SPECIAL_CHARS_SINGLE_QUOTE = setOf('<', '&', '\'')
    }

    override fun getApplicableElements(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        // Check text content nodes
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.TEXT_NODE) {
                val textNode = child as Text
                checkTextContent(context, textNode)
            }
        }

        // Check attributes
        val attributes = element.attributes
        if (attributes != null) {
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as Attr
                checkAttributeValue(context, attr)
            }
        }
    }

    private fun checkTextContent(context: XmlContext, textNode: Text) {
        val content = textNode.nodeValue ?: return

        for (char in content) {
            if (char in XML_TEXT_SPECIAL_CHARS) {
                val escapedChar = getEscapedForm(char)
                val location = context.getLocation(textNode)
                context.report(
                    ISSUE,
                    textNode,
                    location,
                    "The character `$char` must be escaped as `$escapedChar` in XML text content"
                )
                // Report once per text node to avoid flooding
                return
            }
        }
    }

    private fun checkAttributeValue(context: XmlContext, attr: Attr) {
        val value = attr.value ?: return

        // Skip namespace declarations and data binding expressions
        val name = attr.name ?: return
        if (name.startsWith("xmlns:") || name == "xmlns") return

        // Data binding expressions use @{...} or @={...} syntax - skip those
        if (value.trimStart().startsWith("@{") || value.trimStart().startsWith("@={")) return

        // Check for unescaped special characters in attribute values
        // We need to check for raw & and < which are always invalid in attribute values
        for (char in value) {
            if (char == '<' || char == '&') {
                val escapedChar = getEscapedForm(char)
                val location = context.getLocation(attr)
                context.report(
                    ISSUE,
                    attr,
                    location,
                    "The character `$char` must be escaped as `$escapedChar` in XML attribute values"
                )
                return
            }
        }
    }

    private fun getEscapedForm(char: Char): String {
        return when (char) {
            '&' -> "&amp;"
            '<' -> "&lt;"
            '>' -> "&gt;"
            '"' -> "&quot;"
            '\'' -> "&apos;"
            else -> char.toString()
        }
    }
}