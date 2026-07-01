package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary; \
                for example, you can add a `.debug` package suffix in one version and \
                not the other. Therefore, you should not hardcode the application \
                package in the resource; instead, use the special namespace \
                `http://schemas.android.com/apk/res-auto` which will cause the tools \
                to figure out the right namespace for the resource regardless of the \
                actual package used during the build.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val URI_PREFIX = "http://schemas.android.com/apk/res/"
        private const val ANDROID_NS = "android"
        private const val RES_AUTO = "http://schemas.android.com/apk/res-auto"
    }

    override fun getApplicableAttributes(): Collection<String> {
        return XmlScanner.ALL
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val uri = attribute.value
        if (uri != null && uri.startsWith(URI_PREFIX)) {
            val pkg = uri.substring(URI_PREFIX.length)
            if (pkg != ANDROID_NS && pkg.isNotEmpty()) {
                if (context.project.isGradleProject) {
                    val fix = fix()
                        .name("Replace with $RES_AUTO")
                        .replace()
                        .text(uri)
                        .with(RES_AUTO)
                        .autoFix()
                        .build()

                    context.report(
                        ISSUE,
                        attribute,
                        context.getValueLocation(attribute),
                        "Avoid hardcoding the project package name; use `$RES_AUTO` instead",
                        fix
                    )
                }
            }
        }
    }
}