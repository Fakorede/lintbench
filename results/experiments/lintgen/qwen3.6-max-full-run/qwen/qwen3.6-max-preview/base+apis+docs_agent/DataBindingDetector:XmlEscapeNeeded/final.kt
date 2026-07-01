package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class DataBindingDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = "When a string contains characters that have special usage in XML, you must escape the characters.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(DataBindingDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )

        private val UNESCAPED_LT = Regex("<(?![a-zA-Z/?!])")
        private val UNESCAPED_AMP = Regex("&(?!(amp|lt|gt|quot|apos|#\\d+|#x[0-9a-fA-F]+);)")
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val contents = context.getContents() ?: return

        for (match in UNESCAPED_LT.findAll(contents)) {
            val range = match.range
            val location = Location.create(context.file, contents, range.first, range.last + 1)
            context.report(ISSUE, location, "Missing XML escape for special character")
        }

        for (match in UNESCAPED_AMP.findAll(contents)) {
            val range = match.range
            val location = Location.create(context.file, contents, range.first, range.last + 1)
            context.report(ISSUE, location, "Missing XML escape for special character")
        }
    }

    override fun getApplicableElements(): Collection<String>? = null
    override fun getApplicableAttributes(): Collection<String>? = null
}