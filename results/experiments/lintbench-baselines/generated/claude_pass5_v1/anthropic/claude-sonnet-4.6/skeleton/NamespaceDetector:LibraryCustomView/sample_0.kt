package com.android.tools.lint.checks

import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.URI_PREFIX
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element

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
                When using a custom view with custom attributes in a library project, \
                the layout must use the special namespace `${'$'}{AUTO_URI}` instead of a URI \
                which includes the library project's own package. This will be used to \
                automatically adjust the namespace of the attributes when the library \
                resources are merged into the application project.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        // Only check library projects
        val project = context.project
        if (!project.isLibrary) {
            return
        }

        val root = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: Element) {
        // Check all attributes for namespace declarations
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val name = attr.nodeName
            val value = attr.nodeValue

            // Look for xmlns: declarations that use a package-based URI
            if (name.startsWith("xmlns:")) {
                if (value.startsWith(URI_PREFIX) &&
                    value != AUTO_URI &&
                    value != TOOLS_URI
                ) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(attr),
                        "When using a custom view with custom attributes in a library project, " +
                            "the layout must use the special namespace " +
                            "`$AUTO_URI` instead of a URI which includes " +
                            "the library project's own package. This will be used to " +
                            "automatically adjust the namespace of the attributes when " +
                            "the library resources are merged into the application project.",
                    )
                }
            }
        }

        // Recurse into child elements
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                checkElement(context, child)
            }
        }
    }
}