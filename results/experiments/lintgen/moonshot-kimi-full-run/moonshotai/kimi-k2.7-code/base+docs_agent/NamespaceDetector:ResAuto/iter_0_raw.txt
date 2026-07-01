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
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = XmlScanner.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes ?: return
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue

            if (attr.prefix != "xmlns" && attr.name != "xmlns") continue

            val value = attr.value ?: continue
            if (value == RES_AUTO || value == ANDROID_URI) continue
            if (!value.startsWith(RES_PREFIX)) continue

            val fix = LintFix.create()
                .name("Replace with res-auto")
                .replace()
                .range(context.getValueLocation(attr))
                .text(value)
                .with(RES_AUTO)
                .build()

            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "Hardcoded package namespace `$value`; use `$RES_AUTO` instead",
                fix
            )
        }
    }

    companion object {
        private const val RES_PREFIX = "http://schemas.android.com/apk/res/"
        private const val RES_AUTO = "http://schemas.android.com/apk/res-auto"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        @JvmField
        val ISSUE = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded package namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary (for example, you can add a `.debug` package suffix). Do not hardcode the application package in XML namespaces; use `http://schemas.android.com/apk/res-auto` instead, which lets the tools resolve the correct package.
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