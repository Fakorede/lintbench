package com.android.tools.lint.checks

import com.android.SdkConstants.AUTO_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr

private const val PREFIX = "http://schemas.android.com/apk/res/"

class NamespaceDetector : LayoutDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableAttributes(): Collection<String> =
        XmlScannerConstants.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val uri = attribute.namespaceURI ?: return
        if (uri.isEmpty() || !uri.startsWith(PREFIX)) {
            return
        }

        val project = context.project
        if (!project.isLibrary) {
            return
        }

        val pkg = project.getPackage() ?: return
        val expected = PREFIX + pkg
        if (uri == expected) {
            val prefix = attribute.prefix
            val message = if (prefix != null) {
                String.format(
                    "When using a custom view in a library, you must use the " +
                        "`%2\$s` namespace, not the `%1\$s` namespace. " +
                        "Change `xmlns:%3\$s=\"%1\$s\"` to `xmlns:%3\$s=\"%2\$s\"`.",
                    expected, AUTO_URI, prefix
                )
            } else {
                String.format(
                    "When using a custom view in a library, you must use the " +
                        "`%2\$s` namespace, not the `%1\$s` namespace.",
                    expected, AUTO_URI
                )
            }
            context.report(ISSUE, attribute, context.getValueLocation(attribute), message)
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "LibraryCustomView",
            "Custom views in libraries should use res-auto-namespace",
            "When using a custom view with custom attributes in a library project, " +
                "the layout must use the special namespace `$AUTO_URI` instead of a URI which " +
                "includes the library project's own package.",
            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}