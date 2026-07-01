package com.android.tools.lint.checks

import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.URI_PREFIX
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node

class NamespaceDetector : LayoutDetector() {

    companion object {
        val CUSTOM_VIEW = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto-namespace",
            explanation = """
                When using a custom view with custom attributes in a library project, the \
                layout must use the special namespace `$AUTO_URI` instead of a URI which includes \
                the library project's own package. This will be used to automatically adjust \
                the namespace of the attributes when the library resources are merged into \
                the application project.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val project = context.project
        if (!project.isLibrary) {
            return
        }

        val root = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: Element) {
        checkNamespaceDeclarations(context, element)

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                checkElement(context, child as Element)
            }
        }
    }

    private fun checkNamespaceDeclarations(context: XmlContext, element: Element) {
        val attributes: NamedNodeMap = element.attributes ?: return
        val project = context.project

        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val name = attr.name ?: continue
            val value = attr.value ?: continue

            if (!name.startsWith("xmlns:")) {
                continue
            }

            if (value == AUTO_URI || value == TOOLS_URI) {
                continue
            }

            if (value.startsWith(URI_PREFIX)) {
                val packageName = value.substring(URI_PREFIX.length)
                if (isLibraryPackage(project, packageName)) {
                    context.report(
                        CUSTOM_VIEW,
                        element,
                        context.getLocation(attr),
                        "In a library project, the `$AUTO_URI` namespace should be used instead of a URI based on the library's own package"
                    )
                }
            }
        }
    }

    private fun isLibraryPackage(project: Project, packageName: String): Boolean {
        val projectPackage = project.`package`
        if (projectPackage != null && projectPackage == packageName) {
            return true
        }

        for (library in project.allLibraries) {
            val libPackage = library.`package`
            if (libPackage != null && libPackage == packageName) {
                return true
            }
        }

        return false
    }
}