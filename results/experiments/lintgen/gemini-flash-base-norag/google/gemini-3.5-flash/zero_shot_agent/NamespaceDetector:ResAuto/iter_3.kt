package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants.ALL
import org.w3c.dom.Attr

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val RES_AUTO: Issue = Issue.create(
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
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return ALL
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val name = attribute.nodeName
        if (name.startsWith("xmlns:")) {
            val value = attribute.value ?: return
            if (value.startsWith(URI_PREFIX)) {
                val pkg = value.substring(URI_PREFIX.length)
                if (pkg != ANDROID_NS && pkg.isNotEmpty()) {
                    if (pkg == "auto") {
                        val fix = fix()
                            .name("Replace with $AUTO_URI")
                            .replace()
                            .text(value)
                            .with(AUTO_URI)
                            .autoFix()
                            .build()
                        context.report(
                            RES_AUTO,
                            attribute,
                            context.getValueLocation(attribute),
                            "Suspicious namespace: should this be `$AUTO_URI`?",
                            fix
                        )
                    } else if (context.project.isGradleProject) {
                        val fix = fix()
                            .name("Replace with $AUTO_URI")
                            .replace()
                            .text(value)
                            .with(AUTO_URI)
                            .autoFix()
                            .build()
                        context.report(
                            RES_AUTO,
                            attribute,
                            context.getValueLocation(attribute),
                            "In Gradle projects, always use `$AUTO_URI` for custom attributes",
                            fix
                        )
                    }
                }
            }
        }
    }
}