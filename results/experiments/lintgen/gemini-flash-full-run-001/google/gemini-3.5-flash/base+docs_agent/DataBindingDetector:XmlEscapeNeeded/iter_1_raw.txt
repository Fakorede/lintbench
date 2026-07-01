package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class DataBindingDetector : Detector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.VALUES
    }

    override fun run(context: Context) {
        if (context !is XmlContext) return
        val folderType = context.resourceFolderType ?: return
        if (folderType != ResourceFolderType.LAYOUT && folderType != ResourceFolderType.VALUES) return

        val contents = context.getContents() ?: return
        val sanitized = sanitizeContents(contents)

        if (folderType == ResourceFolderType.LAYOUT) {
            checkLayoutFile(context, sanitized)
        } else if (folderType == ResourceFolderType.VALUES) {
            checkValuesFile(context, sanitized)
        }
    }

    private fun sanitizeContents(contents: CharSequence): String {
        val sb = StringBuilder(contents)
        // Sanitize comments
        val commentRegex = """<!--.*?-->""".toRegex(RegexOption.DOT_MATCHES_ALL)
        commentRegex.findAll(contents).forEach { match ->
            val range = match.range
            for (i in range) {
                sb.setCharAt(i, ' ')
            }
        }
        // Sanitize CDATA
        val cdataRegex = """<!\[CDATA\[.*?]]>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        cdataRegex.findAll(sb).forEach { match ->
            val range = match.range
            for (i in range) {
                sb.setCharAt(i, ' ')
            }
        }
        return sb.toString()
    }

    private fun checkLayoutFile(context: XmlContext, sanitized: String) {
        val attributeRegex = """([\w:-]+)\s*=\s*("[^"]*"|'[^']*')""".toRegex()
        val matches = attributeRegex.findAll(sanitized)
        for (match in matches) {
            val quotedValueGroup = match.groups[2] ?: continue
            val quotedValue = quotedValueGroup.value
            val valueStart = quotedValueGroup.range.first
            
            if (quotedValue.length >= 2) {
                val value = quotedValue.substring(1, quotedValue.length - 1)
                val innerStart = valueStart + 1
                checkUnescapedCharacters(context, value, innerStart, allowHtmlTags = false)
            }
        }
    }

    private fun checkValuesFile(context: XmlContext, sanitized: String) {
        val stringRegex = """<(string|item)\b[^>]*>(.*?)</\1>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val matches = stringRegex.findAll(sanitized)
        for (match in matches) {
            val contentGroup = match.groups[2] ?: continue
            val content = contentGroup.value
            val contentStart = contentGroup.range.first
            checkUnescapedCharacters(context, content, contentStart, allowHtmlTags = true)
        }
    }

    private fun checkUnescapedCharacters(
        context: XmlContext,
        value: String,
        offsetShift: Int,
        allowHtmlTags: Boolean
    ) {
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '<') {
                if (allowHtmlTags) {
                    val remaining = value.substring(i)
                    val tagRegex = """^</?[a-zA-Z0-9]+\b[^>]*>""".toRegex()
                    val match = tagRegex.find(remaining)
                    if (match != null) {
                        i += match.value.length
                        continue
                    }
                }
                val start = offsetShift + i
                val end = start + 1
                val location = Location.create(context.file, context.getContents(), start, end)
                context.report(
                    ISSUE,
                    location,
                    "Must escape '<' as '&lt;'"
                )
            } else if (c == '&') {
                if (!isEscapedEntity(value, i)) {
                    val start = offsetShift + i
                    val end = start + 1
                    val location = Location.create(context.file, context.getContents(), start, end)
                    context.report(
                        ISSUE,
                        location,
                        "Must escape '&' as '&amp;'"
                    )
                }
            }
            i++
        }
    }

    private fun isEscapedEntity(text: String, index: Int): Boolean {
        val remaining = text.substring(index)
        val entityRegex = """^&([a-zA-Z0-9]+|#[0-9]+|#x[0-9a-fA-F]+);""".toRegex()
        return entityRegex.containsMatchIn(remaining)
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = "When a string contains characters that have special usage in XML, you must escape the characters.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}