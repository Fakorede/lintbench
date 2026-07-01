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
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node

class NamespaceDetector : ResourceXmlDetector() {

    override fun visitDocument(context: XmlContext, document: Document) {
        // Only check library projects
        if (!context.project.isLibrary) {
            return
        }

        val root = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: Element) {
        val attributes: NamedNodeMap = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val uri = attr.value ?: continue

            // Look for xmlns declarations that use an app-specific URI prefix
            // but are not the res-auto URI
            if (attr.name.startsWith("xmlns:") || attr.nodeName.startsWith("xmlns:")) {
                if (uri.startsWith(URI_PREFIX) && uri != AUTO_URI && uri != TOOLS_URI) {
                    val fix = fix()
                        .name("Replace with res-auto namespace")
                        .replace()
                        .text(uri)
                        .with(AUTO_URI)
                        .build()
                    context.report(
                        ISSUE,
                        attr,
                        context.getValueLocation(attr),
                        "When using a custom view with custom attributes in a library project, " +
                            "the layout must use the special namespace `$AUTO_URI` instead of a " +
                            "URI which includes the library project's own package. This will be " +
                            "used to automatically adjust the namespace of the attributes when " +
                            "the library resources are merged into the application project.",
                        fix
                    )
                }
            }
        }

        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                checkElement(context, child as Element)
            }
            child = child.nextSibling
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto-namespace",
            explanation =
                "When using a custom view with custom attributes in a library project, " +
                    "the layout must use the special namespace `$AUTO_URI` instead of a URI " +
                    "which includes the library project's own package. This will be used to " +
                    "automatically adjust the namespace of the attributes when the library " +
                    "resources are merged into the application project.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}