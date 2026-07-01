package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceFolderType
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val TOOLS_URI = "http://schemas.android.com/tools"
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val URI_PREFIX = "http://schemas.android.com/apk/res/"

        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto-namespace",
            explanation = """
                When using a custom view with custom attributes in a library project, the layout must use the namespace `$AUTO_URI` instead of a URI which includes the library project's own package. This will be used to automatically adjust the namespace of the attributes when the library resources are merged into the application project.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.LAYOUT

    override fun visitDocument(context: XmlContext, document: Document) {
        if (!context.project.isLibrary) {
            return
        }

        val packageName = context.project.getPackage() ?: return
        if (packageName.isEmpty()) {
            return
        }

        val libraryNamespace = URI_PREFIX + packageName
        val root = document.documentElement ?: return
        visitElement(context, root, libraryNamespace)
    }

    private fun visitElement(context: XmlContext, element: Element, libraryNamespace: String) {
        if (isCustomView(element)) {
            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as? Attr ?: continue
                val namespaceUri = attr.namespaceURI
                if (namespaceUri.isNullOrEmpty() ||
                    namespaceUri == ANDROID_URI ||
                    namespaceUri == TOOLS_URI ||
                    namespaceUri == AUTO_URI
                ) {
                    continue
                }
                if (namespaceUri == libraryNamespace) {
                    context.report(
                        ISSUE,
                        attr,
                        context.getLocation(attr),
                        "Library custom view attribute should use the res-auto namespace (`$AUTO_URI`) instead of the library package namespace (`$namespaceUri`).",
                    )
                }
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                visitElement(context, child as Element, libraryNamespace)
            }
        }
    }

    private fun isCustomView(element: Element): Boolean {
        val tag = element.tagName
        if (tag.contains(".")) {
            return true
        }
        if (tag.equals("view", ignoreCase = true)) {
            val className = element.getAttribute("class")
            if (className.isNotEmpty() && !className.startsWith("android.")) {
                return true
            }
        }
        return false
    }
}