package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr

class NamespaceDetector : LayoutDetector() {

    override fun getApplicableAttributes(): Collection<String> = XmlScanner.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val name = attribute.name
        if (name.startsWith(SdkConstants.XMLNS_PREFIX)) {
            val uri = attribute.value
            if (uri != null &&
                uri.startsWith(SdkConstants.URI_PREFIX) &&
                uri != SdkConstants.ANDROID_URI &&
                uri != SdkConstants.AUTO_URI
            ) {
                if (context.project.isLibrary) {
                    val fix = fix()
                        .replace()
                        .with(SdkConstants.AUTO_URI)
                        .name("Update to res-auto")
                        .build()

                    context.report(
                        ISSUE,
                        attribute,
                        context.getValueLocation(attribute),
                        "In library projects, custom attributes should specify `http://schemas.android.com/apk/res-auto` instead of the library's package",
                        fix
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto-namespace",
            explanation = """
                When using a custom view with custom attributes in a library project, the \
                layout must use the special namespace `http://schemas.android.com/apk/res-auto` \
                instead of a URI which includes the library project's own package. This will \
                be used to automatically adjust the namespace of the attributes when the library \
                resources are merged into the application project.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.LAYOUT_RESOURCE_FILES
            )
        )
    }
}