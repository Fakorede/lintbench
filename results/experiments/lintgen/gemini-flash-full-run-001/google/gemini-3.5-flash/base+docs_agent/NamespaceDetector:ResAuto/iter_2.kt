package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr

class NamespaceDetector : Detector(), XmlScanner {

    override fun getApplicableAttributes(): Collection<String>? {
        return XmlScannerConstants.ALL
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val name = attribute.name
        if (name.startsWith("xmlns:")) {
            val value = attribute.value
            if (value == "http://schemas.android.com/apk/res/auto") {
                val fix = fix()
                    .replace()
                    .text(value)
                    .with("http://schemas.android.com/apk/res-auto")
                    .build()

                context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Use the res-auto namespace http://schemas.android.com/apk/res-auto instead",
                    fix
                )
            } else if (value.startsWith("http://schemas.android.com/apk/res/") &&
                value != "http://schemas.android.com/apk/res/android"
            ) {
                if (context.project.isGradleProject) {
                    val fix = fix()
                        .replace()
                        .text(value)
                        .with("http://schemas.android.com/apk/res-auto")
                        .build()

                    context.report(
                        ISSUE,
                        attribute,
                        context.getValueLocation(attribute),
                        "In Gradle projects, use the res-auto namespace http://schemas.android.com/apk/res-auto instead of the application package",
                        fix
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary; for \
                example, you can add a `.debug` package suffix in one version and not the other. \
                Therefore, you should not hardcode the application package in the resource; \
                instead, use the special namespace `http://schemas.android.com/apk/res-auto` \
                which will cause the tools to figure out the right namespace for the resource \
                regardless of the actual package used during the build.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}