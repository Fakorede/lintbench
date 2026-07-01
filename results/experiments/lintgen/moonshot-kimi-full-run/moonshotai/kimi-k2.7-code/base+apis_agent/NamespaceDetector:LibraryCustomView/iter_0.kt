package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.AUTO_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr

class NamespaceDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String>? = null

    override fun getApplicableAttributes(): Collection<String>? = ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (!context.project.isLibrary) {
            return
        }

        // Only look at namespace declarations: xmlns:<prefix>="..."
        if (attribute.prefix != XMLNS_PREFIX) {
            return
        }

        val namespaceUri = attribute.value ?: return

        // android and res-auto namespaces are fine
        if (namespaceUri == ANDROID_URI || namespaceUri == AUTO_URI) {
            return
        }

        // Library package namespace URI: http://schemas.android.com/apk/res/<package>
        if (!namespaceUri.startsWith(URI_PREFIX)) {
            return
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