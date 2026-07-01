package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.regex.Pattern

class DataBindingDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            checkNode(context, attributes.item(i))
        }
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.TEXT_NODE) {
                checkNode(context, child)
            }
            child = child.nextSibling
        }
    }

    private fun checkNode(context: XmlContext, node: Node) {
        val location = context.getLocation(node)
        val startOffset = location.start?.offset ?: return
        val endOffset = location.end?.offset ?: return
        if (startOffset >= endOffset) return

        val contents = context.contents ?: return
        val raw = contents.substring(startOffset, endOffset)

        val ampMatcher = UNESCAPED_AMP.matcher(raw)
        if (ampMatcher.find()) {
            val offset = startOffset + ampMatcher.start()
            val loc = Location.create(context.file, contents, offset, offset + 1)
            context.report(ISSUE, loc, "Unescaped '&' character. Use `&amp;` instead.")
            return
        }

        val ltMatcher = UNESCAPED_LT.matcher(raw)
        if (ltMatcher.find()) {
            val offset = startOffset + ltMatcher.start()
            val loc = Location.create(context.file, contents, offset, offset + 1)
            context.report(ISSUE, loc, "Unescaped '<' character. Use `&lt;` instead.")
            return
        }
    }

    companion object {
        private val UNESCAPED_AMP = Pattern.compile("&(?!(amp|lt|gt|quot|apos|#\\d+|#x[\\da-fA-F]+);)")
        private val UNESCAPED_LT = Pattern.compile("<")

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = "When a string contains characters that have special usage in XML, you must escape the characters.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}