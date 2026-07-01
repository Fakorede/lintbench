package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Text

class DataBindingDetector : Detector(), Detector.XmlScanner {

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

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        checkNode(context, attribute)
    }

    override fun visitText(context: XmlContext, text: Text) {
        checkNode(context, text)
    }

    private fun checkNode(context: XmlContext, node: org.w3c.dom.Node) {
        val location = context.getLocation(node)
        val startOffset = location.start?.offset ?: return
        val endOffset = location.end?.offset ?: return
        val contents = context.getContents()

        if (startOffset < 0 || endOffset > contents.length || startOffset >= endOffset) return

        val rawText = contents.substring(startOffset, endOffset)
        if (hasUnescapedXmlChars(rawText)) {
            context.report(ISSUE, location, "Missing XML Escape")
        }
    }

    private fun hasUnescapedXmlChars(raw: String): Boolean {
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '<' || c == '>') return true
            if (c == '&') {
                if (!isValidEntity(raw, i)) return true
            }
            i++
        }
        return false
    }

    private fun isValidEntity(raw: String, start: Int): Boolean {
        if (start + 1 >= raw.length) return false
        val next = raw[start + 1]
        if (next == '#') return true

        val entities = listOf("lt;", "gt;", "amp;", "quot;", "apos;")
        for (entity in entities) {
            if (raw.regionMatches(start + 1, entity, 0, entity.length, ignoreCase = false)) {
                return true
            }
        }
        return false
    }
}