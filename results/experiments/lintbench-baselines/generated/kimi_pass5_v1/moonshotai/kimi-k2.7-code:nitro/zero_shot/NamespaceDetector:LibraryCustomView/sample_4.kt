package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceFolderType
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableElements(): Collection<String> =
        XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.project.isLibrary) {
            return
        }

        val tag = element.tagName
        if (!tag.contains(".") || tag.startsWith(ANDROID_FRAMEWORK_PREFIX)) {
            return
        }

        val libPackage = context.project.getPackage() ?: return

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val uri = attr.namespaceURI ?: continue

            if (uri == AUTO_URI || uri == ANDROID_URI || uri == TOOLS_URI) {
                continue
            }

            if (uri.contains(libPackage)) {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "Custom views in libraries must use the \"$AUTO_URI\" namespace " +
                            "for custom attributes, not a URI containing the library package " +
                            "(found \"$uri\")."
                )
            }
        }
    }

    companion object {
        private const val ANDROID_FRAMEWORK_PREFIX = "android."

        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE
        )

        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto-namespace",
            explanation = """
                When using a custom view with custom attributes in a library project,
                the layout must use the special namespace $AUTO_URI instead of a URI
                which includes the library project's own package. This will be used to
                automatically adjust the namespace of the attributes when the library
                resources are merged into the application project.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION
        )
    }
}