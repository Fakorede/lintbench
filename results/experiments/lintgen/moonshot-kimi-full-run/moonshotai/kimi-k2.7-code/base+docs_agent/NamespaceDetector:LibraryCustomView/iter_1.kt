package com.android.tools.lint.checks

import com.android.SdkConstants.AUTO_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.XmlScannerConstants.ALL
import org.w3c.dom.Attr

class NamespaceDetector : Detector(), XmlScanner {

    override fun getApplicableAttributes(): Collection<String>? = ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (context.resourceFolderType != ResourceFolderType.LAYOUT) {
            return
        }
        if (!context.project.isLibrary) {
            return
        }
        val pkg = context.project.getPackage() ?: return
        val libraryUri = RESOURCE_NS_PREFIX + pkg

        if (attribute.prefix == "xmlns" && attribute.value == libraryUri) {
            context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "When using a custom view with custom attributes in a library project, " +
                        "use the `$AUTO_URI` namespace instead of `$libraryUri`."
            )
        }
    }

    companion object {
        private const val RESOURCE_NS_PREFIX = "http://schemas.android.com/apk/res/"

        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Library custom views should use the res-auto namespace",
            explanation = """
                When using a custom view with custom attributes in a library project, the layout
                must use the special namespace `${AUTO_URI}` instead of a URI which includes the
                library project's own package. This allows the build system to automatically adjust
                the namespace of the attributes when the library resources are merged into the
                application project.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}