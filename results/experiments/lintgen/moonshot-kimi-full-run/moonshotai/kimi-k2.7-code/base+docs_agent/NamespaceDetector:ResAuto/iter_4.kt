package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes ?: return
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val name = attr.name ?: continue
            if (!name.startsWith(XMLNS_PREFIX)) {
                continue
            }

            val value = attr.value ?: continue
            if (value.startsWith(AUTO_URI) || !value.startsWith(RESOURCE_PREFIX)) {
                continue
            }

            val fix = LintFix.create()
                .name("Replace with res-auto")
                .replace()
                .text(value)
                .with(AUTO_URI)
                .build()

            context.report(
                RES_AUTO,
                attr,
                context.getValueLocation(attr),
                "Hardcoded package namespace: should use \"$AUTO_URI\" instead of \"$value\"",
                fix
            )
        }
    }

    companion object {
        private const val XMLNS_PREFIX = "xmlns"
        private const val RESOURCE_PREFIX = "http://schemas.android.com/apk/res/"
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"

        @JvmField
        val RES_AUTO: Issue = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded package namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary; for example, you can add a `.debug` package suffix in one version and not the other. Therefore, you should not hardcode the application package in the resource; instead, use the special namespace `http://schemas.android.com/apk/res-auto` which will cause the tools to figure out the right namespace for the resource regardless of the actual package used during the build.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}