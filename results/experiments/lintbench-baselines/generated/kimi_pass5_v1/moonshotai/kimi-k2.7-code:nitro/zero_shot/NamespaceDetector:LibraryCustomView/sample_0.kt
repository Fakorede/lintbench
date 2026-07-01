package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TOOLS_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr
import org.w3c.dom.Element

private const val RES_AUTO_PREFIX = "http://schemas.android.com/apk/res/"

class NamespaceDetector : ResourceXmlDetector() {

    override fun appliesTo(context: Context): Boolean = context.project.isLibrary

    override fun getApplicableElements(): Collection<String> = XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.resourceFolderType != ResourceFolderType.LAYOUT) {
            return
        }

        val tag = element.tagName
        if (tag.isNullOrBlank() || !tag.contains(".")) {
            return
        }

        val project = context.project
        val packageName = project.`package` ?: return
        val libraryNs = "$RES_AUTO_PREFIX$packageName"

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val ns = attr.namespaceURI ?: continue
            if (ns.isEmpty() || ns == ANDROID_URI || ns == TOOLS_URI || ns == AUTO_URI) {
                continue
            }

            if (ns == libraryNs) {
                context.report(
                    ISSUE_LIBRARY_CUSTOM_VIEW,
                    attr,
                    context.getLocation(attr),
                    "When using a custom view with custom attributes in a library project, " +
                        "the layout must use the res-auto namespace ($AUTO_URI) instead of " +
                        "the package-specific namespace `$ns`."
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE_LIBRARY_CUSTOM_VIEW = Issue.create(
            id = "LibraryCustomView",
            summary = "Custom views in libraries should use res-auto-namespace",
            explanation = """
                When using a custom view with custom attributes in a library project, the
                layout must use the special namespace $AUTO_URI instead of a URI which includes
                the library project's own package. This will be used to automatically adjust
                the namespace of the attributes when the library resources are merged into the
                application project.
            """.trimIndent(),
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