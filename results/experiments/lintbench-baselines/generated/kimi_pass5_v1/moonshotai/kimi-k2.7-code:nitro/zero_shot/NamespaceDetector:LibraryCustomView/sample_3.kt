package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr
import java.io.File

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val TOOLS_URI = "http://schemas.android.com/tools"
        private const val RES_PKG_PREFIX = "http://schemas.android.com/apk/res/"

        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto-namespace",
            explanation = """
                When using a custom view with custom attributes in a library project, the \
                layout must use the special namespace `$AUTO_URI` instead of a URI which \
                includes the library project's own package. This will allow the build system to \
                automatically adjust the namespace of the attributes when the library resources \
                are merged into the application project.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun appliesTo(context: Context, file: File): Boolean {
        return context.project.isLibrary
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return XmlScannerConstants.ALL
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val element = attribute.ownerElement ?: return
        val tag = element.tagName
        if (!tag.contains('.')) {
            return
        }

        val uri = attribute.namespaceURI ?: return
        if (uri.isEmpty() ||
            uri == ANDROID_URI ||
            uri == TOOLS_URI ||
            uri == AUTO_URI) {
            return
        }

        if (!uri.startsWith(RES_PKG_PREFIX)) {
            return
        }

        val pkg = context.project.getPackage()
        if (uri != RES_PKG_PREFIX + pkg) {
            return
        }

        context.report(
            ISSUE,
            attribute,
            context.getValueLocation(attribute),
            "When using a custom view in a library, attribute namespaces must be " +
                    "`res-auto` (`$AUTO_URI`), not a package-specific URI."
        )
    }
}