package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
private const val LIBRARY_URI_PREFIX = "http://schemas.android.com/apk/res/"

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto-namespace",
            explanation = """
                When a library project uses a custom view with custom attributes, the layout
                must use the "$AUTO_URI" namespace. Using a package-specific namespace such as
                "$LIBRARY_URI_PREFIX<library-package>" will not work once the library resources
                are merged into the consuming application, because the application package is
                different and the attributes will not be resolved.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        if (!context.project.isLibrary) {
            return
        }

        val packageName = context.project.`package` ?: return
        val libraryNamespace = "$LIBRARY_URI_PREFIX$packageName"
        val root = document.documentElement ?: return

        checkElement(context, root, libraryNamespace)
    }

    private fun checkElement(context: XmlContext, element: Element, libraryNamespace: String) {
        val tagName = element.tagName
        val customView = isCustomView(tagName)

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue

            if (attr.prefix == "xmlns" && attr.value == libraryNamespace) {
                reportNamespaceIssue(context, attr, libraryNamespace)
                continue
            }

            if (customView) {
                val namespaceUri = attr.namespaceURI
                if (namespaceUri == libraryNamespace) {
                    reportNamespaceIssue(context, attr, libraryNamespace)
                }
            }
        }

        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                checkElement(context, child as Element, libraryNamespace)
            }
            child = child.nextSibling
        }
    }

    private fun isCustomView(tagName: String): Boolean {
        if (tagName.contains(".")) {
            return true
        }
        return tagName.isNotEmpty() && Character.isUpperCase(tagName[0])
    }

    private fun reportNamespaceIssue(context: XmlContext, attr: Attr, libraryNamespace: String) {
        val message = "Use $AUTO_URI instead of $libraryNamespace for custom view attributes"
        context.report(ISSUE, context.getLocation(attr), message)
    }
}