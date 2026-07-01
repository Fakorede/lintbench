package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.AUTO_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class NamespaceDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String>? = null

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun visitDocument(context: XmlContext, document: Document) {
        if (!context.project.isLibrary) {
            return
        }

        visitElementRecursive(context, document.documentElement)
    }

    private fun visitElementRecursive(context: XmlContext, element: Element?) {
        if (element == null) {
            return
        }

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attribute = attributes.item(i) as? Attr ?: continue
            if (attribute.prefix != XMLNS_PREFIX) {
                continue
            }

            val namespaceUri = attribute.value ?: continue
            if (namespaceUri == ANDROID_URI || namespaceUri == AUTO_URI) {
                continue
            }

            if (!namespaceUri.startsWith(URI_PREFIX)) {
                continue
            }

            context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "When using a custom view with custom attributes in a library project, " +
                        "the layout must use the `$AUTO_URI` namespace instead of `$namespaceUri`. " +
                        "This will be used to automatically adjust the namespace of the attributes " +
                        "when the library resources are merged into the application project."
            )
        }

        var child = element.firstChild
        while (child != null) {
            if (child is Element) {
                visitElementRecursive(context, child)
            }
            child = child.nextSibling
        }
    }

    companion object {
        private const val XMLNS_PREFIX = "xmlns"
        private const val URI_PREFIX = "http://schemas.android.com/apk/res/"

        @JvmField
        val ISSUE = Issue.create(
            "LibraryCustomView",
            "Library custom views should use the res-auto namespace",
            "When using a custom view with custom attributes in a library project, " +
                    "the layout must use the special namespace `$AUTO_URI` instead of a URI " +
                    "which includes the library project's own package. This will be used to " +
                    "automatically adjust the namespace of the attributes when the library " +
                    "resources are merged into the application project.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}