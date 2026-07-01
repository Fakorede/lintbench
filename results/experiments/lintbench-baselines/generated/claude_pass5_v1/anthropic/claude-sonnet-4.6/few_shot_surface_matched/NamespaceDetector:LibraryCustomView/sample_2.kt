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
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node

class NamespaceDetector : ResourceXmlDetector(), XmlScanner {

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT
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
        val attributes: NamedNodeMap = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val uri = attr.namespaceURI ?: continue

            // Check for xmlns declarations that use app-specific package URIs
            // (i.e., start with URI_PREFIX but are not AUTO_URI or TOOLS_URI)
            if (attr.name.startsWith("xmlns:")) {
                val value = attr.value ?: continue
                if (value.startsWith(URI_PREFIX) &&
                    value != AUTO_URI &&
                    value != TOOLS_URI
                ) {
                    context.report(
                        ISSUE,
                        attr,
                        context.getValueLocation(attr),
                        "When using a custom view with custom attributes in a library project, " +
                            "the layout must use the special namespace `$AUTO_URI` instead of a " +
                            "URI which includes the library project's own package. This will be " +
                            "used to automatically adjust the namespace of the attributes when " +
                            "the library resources are merged into the application project."
                    )
                }
            }
        }

        // Recurse into children
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