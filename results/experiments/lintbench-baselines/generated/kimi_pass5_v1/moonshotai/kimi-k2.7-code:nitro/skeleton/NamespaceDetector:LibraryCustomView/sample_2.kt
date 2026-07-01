package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        private const val PACKAGE_NS_PREFIX = "http://schemas.android.com/apk/res/"
        private const val AUTO_NS = "http://schemas.android.com/apk/res-auto"

        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto-namespace",
            explanation = "When using a custom view with custom attributes in a library project, the layout must use the special namespace `$AUTO_NS` instead of a URI which includes the library project's own package. This will be used to automatically adjust the namespace of the attributes when the library resources are merged into the application project.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )

        private const val MESSAGE =
            "Should use `$AUTO_NS` instead of a project-specific namespace in library projects"
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        if (!context.project.isLibrary) {
            return
        }

        val pkg = context.project.getPackage() ?: return
        val pkgUri = "$PACKAGE_NS_PREFIX$pkg"
        val root = document.documentElement ?: return

        checkElement(context, root, pkgUri)
    }

    private fun checkElement(
        context: XmlContext,
        element: org.w3c.dom.Element,
        pkgUri: String,
    ) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as org.w3c.dom.Attr
            if (isProjectNamespaceAttribute(attr, pkgUri)) {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    MESSAGE,
                )
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                checkElement(context, child as org.w3c.dom.Element, pkgUri)
            }
        }
    }

    private fun isProjectNamespaceAttribute(attr: org.w3c.dom.Attr, pkgUri: String): Boolean {
        if (attr.namespaceURI == pkgUri) {
            return true
        }

        val name = attr.name
        if ((name == "xmlns" || name.startsWith("xmlns:")) && attr.value == pkgUri) {
            return true
        }

        return false
    }
}