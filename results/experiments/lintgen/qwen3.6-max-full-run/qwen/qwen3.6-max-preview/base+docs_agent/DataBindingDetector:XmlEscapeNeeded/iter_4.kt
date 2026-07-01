package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

class DataBindingDetector : Detector(), XmlScanner {

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

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.TEXT_NODE) {
                checkNode(context, child, context.getLocation(child))
            }
        }
    }

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        checkNode(context, attribute, context.getValueLocation(attribute))
    }

    private fun checkNode(context: XmlContext, node: Node, location: Location) {
        val start = location.start?.offset ?: return
        val end = location.end?.offset ?: return
        val contents = context.getContents() ?: return
        if (start < 0 || end < 0 || start >= end || end > contents.length) return

        val rawValue = contents.subSequence(start, end).toString()
        val unescaped = findUnescapedChar(rawValue)
        if (unescaped != null) {
            context.report(ISSUE, location, "Missing XML escape for special character '$unescaped'")
        }
    }

    private fun findUnescapedChar(s: String): Char? {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '<') return '<'
            if (c == '&') {
                val semi = s.indexOf(';', i + 1)
                if (semi == -1) return '&'
                val entity = s.substring(i, semi + 1)
                if (!isValidEntity(entity)) return '&'
                i = semi + 1
            } else {
                i++
            }
        }
        return null
    }

    private fun isValidEntity(entity: String): Boolean {
        return entity == "&amp;" || entity == "&lt;" || entity == "&gt;" ||
               entity == "&quot;" || entity == "&apos;" ||
               entity.startsWith("&#")
    }
}