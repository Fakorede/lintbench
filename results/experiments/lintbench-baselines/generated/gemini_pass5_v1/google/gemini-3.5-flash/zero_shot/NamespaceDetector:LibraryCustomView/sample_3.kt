package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.AUTO_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : LayoutDetector() {

    override fun getApplicableElements(): Collection<String>? {
        return ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.project.isLibrary) {
            return
        }

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val name = attr.nodeName
            if (name.startsWith("xmlns:")) {
                val value = attr.nodeValue
                if (value.startsWith("http://schemas.android.com/apk/res/") && value != ANDROID_URI) {
                    val fix = fix()
                        .replace()
                        .text(value)
                        .with(AUTO_URI)
                        .name("Use $AUTO_URI")
                        .build()

                    context.report(
                        ISSUE,
                        attr,
                        context.getValueLocation(attr),
                        "In libraries, custom attributes must use the `$AUTO_URI` URI instead of the application project's own URI",
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
            briefDescription = "Using Custom Library View Attributes",
            explanation = """
                When using a custom view with custom attributes in a library project, the \
                layout must use the special namespace `http://schemas.android.com/apk/res-auto` \
                instead of a URI which includes the library project's own package. \
                This will be used to automatically adjust the namespace of the attributes \
                when the library resources are merged into the application project.
            """.trimIndent(),
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