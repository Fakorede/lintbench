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

class NamespaceDetector : Detector(), XmlScanner {

    override fun getApplicableAttributes(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val name = attribute.name
        if (name != "xmlns" && !name.startsWith("xmlns:")) {
            return
        }

        val value = attribute.value ?: return
        if (value == ANDROID_URI || value == AUTO_URI) {
            return
        }
        if (!value.startsWith(RES_PREFIX)) {
            return
        }

        val fix = LintFix.create()
            .name("Replace with res-auto")
            .replace()
            .range(context.getValueLocation(attribute))
            .text(value)
            .with(AUTO_URI)
            .build()

        context.report(
            RES_AUTO,
            attribute,
            context.getValueLocation(attribute),
            "Should use \"$AUTO_URI\" instead of \"$value\"",
            fix
        )
    }

    companion object {
        private const val RES_PREFIX = "http://schemas.android.com/apk/res/"
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        @JvmField
        val RES_AUTO = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded package namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary; for example, you can add a `.debug` package suffix in one version and not the other. Therefore, you should not hardcode the application package in XML resources. Instead, use the special namespace `http://schemas.android.com/apk/res-auto`, which lets the tools figure out the right namespace regardless of the actual package used during the build.
            """,
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