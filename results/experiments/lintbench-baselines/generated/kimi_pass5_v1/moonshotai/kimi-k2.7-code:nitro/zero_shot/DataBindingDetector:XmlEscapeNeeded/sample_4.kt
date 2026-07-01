package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class DataBindingDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.VALUES

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.TAG_STRING)

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes ?: return
        for (i in 0 until children.length) {
            val child = children.item(i) ?: continue
            if (child.nodeType != Node.TEXT_NODE) {
                continue
            }

            val raw = getRawText(context, child) ?: continue
            val offset = findUnescapedSpecialCharacter(raw)
            if (offset != -1) {
                val location = context.getLocation(child)
                context.report(
                    ISSUE,
                    location,
                    "Missing XML escape: the string contains '${raw[offset]}' which must be escaped."
                )
                break
            }
        }
    }

    private fun getRawText(context: XmlContext, node: Node): String? {
        val location: Location = context.getLocation(node)
        val start = location.start ?: return null
        val end = location.end ?: return null
        val contents = context.contents ?: return null
        val startOffset = start.offset
        val endOffset = end.offset
        if (startOffset < 0 || endOffset > contents.length || startOffset >= endOffset) {
            return null
        }
        return contents.substring(startOffset, endOffset)
    }

    private fun findUnescapedSpecialCharacter(raw: String): Int {
        var i = 0
        while (i < raw.length) {
            when (raw[i]) {
                '<' -> return i
                '&' -> {
                    val end = raw.indexOf(';', i + 1)
                    if (end == -1 || !isEntityReference(raw, i, end)) {
                        return i
                    }
                    i = end
                }
            }
            i++
        }
        return -1
    }

    private fun isEntityReference(raw: String, ampIndex: Int, semiIndex: Int): Boolean {
        if (semiIndex <= ampIndex + 1) {
            return false
        }
        for (j in ampIndex + 1 until semiIndex) {
            val c = raw[j]
            if (!(c.isLetterOrDigit() || c == '#')) {
                return false
            }
        }
        return true
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "XmlEscapeNeeded",
            "Missing XML Escape",
            """
                When a string contains characters that have special usage in XML,
                you must escape the characters (for example, use &lt; and &amp;).
            """.trimIndent(),
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}