package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return XmlScannerConstants.ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.project.isLibrary || context.resourceFolderType != ResourceFolderType.LAYOUT) {
            return
        }

        val tag = element.tagName
        if (!tag.contains(".")) {
            return
        }

        val packageName = context.project.`package`
        val packageNamespace = "http://schemas.android.com/apk/res/$packageName"
        val reportedNamespaces = mutableSetOf<String>()

        for (i in 0 until element.attributes.length) {
            val attr = element.attributes.item(i) as? Attr ?: continue
            val namespaceUri = attr.namespaceURI ?: continue
            if (namespaceUri == packageNamespace && reportedNamespaces.add(namespaceUri)) {
                reportNamespaceIssue(context, element, attr, namespaceUri)
            }
        }
    }

    private fun reportNamespaceIssue(
        context: XmlContext,
        element: Element,
        attr: Attr,
        packageNamespace: String
    ) {
        val prefix = attr.prefix ?: return
        var current: Element? = element
        while (current != null) {
            val xmlnsAttr = current.getAttributeNodeNS(XMLNS_URI, prefix)
            if (xmlnsAttr != null && xmlnsAttr.value == packageNamespace) {
                val fix = fix()
                    .replace()
                    .text(packageNamespace)
                    .with(SdkConstants.AUTO_URI)
                    .build()
                context.report(
                    ISSUE,
                    xmlnsAttr,
                    context.getLocation(xmlnsAttr),
                    "When using a custom view in a library, you should use the " +
                            "`${SdkConstants.AUTO_URI}` namespace instead of the library " +
                            "package namespace.",
                    fix
                )
                return
            }
            current = current.parentNode as? Element
        }
    }

    companion object {
        private const val XMLNS_URI = "http://www.w3.org/2000/xmlns/"

        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use the res-auto namespace",
            explanation = "When using a custom view with custom attributes in a library project, the " +
                    "layout must use the special `${SdkConstants.AUTO_URI}` namespace instead of a " +
                    "URI which includes the library project's own package. This will be used to " +
                    "automatically adjust the namespace of the attributes when the library " +
                    "resources are merged into the application project.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}