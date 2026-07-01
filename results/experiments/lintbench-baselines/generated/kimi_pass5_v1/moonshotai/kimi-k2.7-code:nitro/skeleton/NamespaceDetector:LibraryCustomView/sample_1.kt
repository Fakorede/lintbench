package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceFolderType
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val RES_URI_PREFIX = "http://schemas.android.com/apk/res/"
        private const val VIEW_TAG = "view"
        private const val ATTR_CLASS = "class"

        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto-namespace",
            explanation = "When using a custom view with custom attributes in a library project, the layout must use the namespace $AUTO_URI instead of a URI which includes the library project's own package. This will be used to automatically adjust the namespace of the attributes when the library resources are merged into the application project.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        if (!context.project.isLibrary) {
            return
        }

        val root = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: org.w3c.dom.Element) {
        if (isCustomView(element)) {
            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i)
                val namespaceUri = attr.namespaceURI ?: continue
                if (namespaceUri == ANDROID_URI || namespaceUri == AUTO_URI) {
                    continue
                }
                if (namespaceUri.startsWith(RES_URI_PREFIX)) {
                    val message = "When using a custom view with custom attributes in a library project, you must use the $AUTO_URI namespace, not $namespaceUri"
                    context.report(ISSUE, attr, context.getLocation(attr), message)
                }
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                checkElement(context, child as org.w3c.dom.Element)
            }
        }
    }

    private fun isCustomView(element: org.w3c.dom.Element): Boolean {
        val tag = element.tagName
        if (tag.indexOf('.') != -1) {
            return true
        }
        if (tag == VIEW_TAG) {
            val className = element.getAttribute(ATTR_CLASS)
            if (className.isNotEmpty() && className.indexOf('.') != -1) {
                return true
            }
        }
        return false
    }
}