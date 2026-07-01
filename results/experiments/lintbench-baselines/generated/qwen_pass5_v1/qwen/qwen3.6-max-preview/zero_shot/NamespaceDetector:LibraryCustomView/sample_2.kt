package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class NamespaceDetector : LayoutDetector() {

    companion object {
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val RES_PREFIX = "http://schemas.android.com/apk/res/"

        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto-namespace",
            explanation = """
                When using a custom view with custom attributes in a library project, \
                the layout must use the special namespace `$AUTO_URI` instead of a URI which includes \
                the library project's own package. This will be used to automatically adjust \
                the namespace of the attributes when the library resources are merged into \
                the application project.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("*")

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode
        if (parent != null && parent.nodeType != Node.DOCUMENT_NODE) {
            return
        }

        if (!context.project.isLibrary) return

        val packageName = context.project.packageName ?: return
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val name = attr.nodeName
            if (name.startsWith("xmlns:")) {
                val value = attr.nodeValue
                if (value != AUTO_URI && value.startsWith(RES_PREFIX) && value.contains(packageName)) {
                    context.report(
                        ISSUE,
                        context.getLocation(attr),
                        "Use `$AUTO_URI` instead of `$value` in library projects"
                    )
                }
            }
        }
    }
}