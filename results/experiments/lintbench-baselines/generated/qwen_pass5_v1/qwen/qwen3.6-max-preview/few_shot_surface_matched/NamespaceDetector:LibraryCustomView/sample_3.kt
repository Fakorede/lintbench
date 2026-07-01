package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document

class NamespaceDetector : ResourceXmlDetector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val attributes = root.attributes
        val packageName = context.packageName ?: return

        val targetUri = "http://schemas.android.com/apk/res/$packageName"

        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            if (attr.nodeValue == targetUri) {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "Use `$AUTO_URI` instead of `$targetUri` for custom attributes in library projects"
                )
            }
        }
    }

    companion object {
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"

        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto-namespace",
            explanation = "When using a custom view with custom attributes in a library project, " +
                "the layout must use the special namespace $AUTO_URI instead of a URI which includes " +
                "the library project's own package. This will be used to automatically adjust " +
                "the namespace of the attributes when the library resources are merged into " +
                "the application project.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}