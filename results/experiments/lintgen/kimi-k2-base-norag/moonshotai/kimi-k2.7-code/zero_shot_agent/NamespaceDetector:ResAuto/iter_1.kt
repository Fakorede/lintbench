package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary; for example, \
                you can add a `.debug` package suffix in one version and not the other. Therefore, \
                you should not hardcode the application package in the resource; instead, use the \
                special namespace `http://schemas.android.com/apk/res-auto` which will cause the \
                tools to figure out the right namespace for the resource regardless of the actual \
                package used during the build.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val PACKAGE_URI_PREFIX = "http://schemas.android.com/apk/res/"
        private const val ANDROID_FRAMEWORK_URI = "http://schemas.android.com/apk/res/android"
        private const val XMLNS_URI = "http://www.w3.org/2000/xmlns/"
    }

    override fun getApplicableElements(): Collection<String>? {
        return XmlScannerConstants.ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes ?: return
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            if (!isNamespaceDeclaration(attr)) continue

            val value = attr.value ?: continue
            if (value == ANDROID_FRAMEWORK_URI || value == AUTO_URI) {
                continue
            }
            if (value.startsWith(PACKAGE_URI_PREFIX) || value.startsWith(AUTO_URI)) {
                val fix = LintFix.create()
                    .replace()
                    .text(value)
                    .with(AUTO_URI)
                    .build()

                context.report(
                    ISSUE,
                    attr,
                    context.getValueLocation(attr),
                    "Should use `$AUTO_URI` instead of `$value`",
                    fix
                )
            }
        }
    }

    private fun isNamespaceDeclaration(attr: Attr): Boolean {
        return attr.namespaceURI == XMLNS_URI ||
                attr.name == "xmlns" ||
                attr.name.startsWith("xmlns:")
    }
}