package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val PACKAGE_URI_PREFIX = "http://schemas.android.com/apk/res/"
        private const val XMLNS_PREFIX = "xmlns:"

        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto-namespace",
            explanation = """
                When a library project defines custom views with custom attributes, the \
                layout must use the '$AUTO_URI' namespace. Using a namespace that contains \
                the library's own package name will not work correctly after the library \
                resources are merged into the application project.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        if (!context.project.isLibrary) {
            return
        }

        val root = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: org.w3c.dom.Element) {
        val attrs = element.attributes ?: return
        val libraryPackage = context.project.getPackage()

        for (i in 0 until attrs.length) {
            val attr = attrs.item(i) as? org.w3c.dom.Attr ?: continue
            if (!attr.name.startsWith(XMLNS_PREFIX)) {
                continue
            }

            val value = attr.value
            if (value.startsWith(PACKAGE_URI_PREFIX)) {
                val namespacePackage = value.substring(PACKAGE_URI_PREFIX.length)
                if (namespacePackage == libraryPackage) {
                    context.report(
                        ISSUE,
                        context.getLocation(attr),
                        "Custom views in libraries must use '$AUTO_URI' instead of a namespace containing the library package name"
                    )
                }
            }
        }

        val children = element.childNodes ?: return
        for (i in 0 until children.length) {
            val child = children.item(i) as? org.w3c.dom.Element ?: continue
            checkElement(context, child)
        }
    }
}