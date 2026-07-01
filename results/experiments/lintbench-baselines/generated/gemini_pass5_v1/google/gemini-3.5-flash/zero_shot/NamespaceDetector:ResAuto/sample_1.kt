package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
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
                for example, you can add a `.debug` package suffix in one version and not the other. \
                Therefore, you should not hardcode the application package in the resource; \
                instead, use the special namespace `http://schemas.android.com/apk/res-auto` \
                which will cause the tools to figure out the right namespace for the resource \
                regardless of the actual package used during the build.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = com.android.tools.lint.detector.api.Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val URI_PREFIX = "http://schemas.android.com/apk/res/"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return true
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return XmlScanner.ALL
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val name = attribute.name
        if (name.startsWith("xmlns:") || name == "xmlns") {
            val value = attribute.value
            if (value.startsWith(URI_PREFIX) && value != ANDROID_URI) {
                val fix = fix()
                    .replace()
                    .text(value)
                    .with(AUTO_URI)
                    .autoFix()
                    .build()

                context.report(
                    issue = ISSUE,
                    scope = attribute,
                    location = context.getValueLocation(attribute),
                    message = "Should use `http://schemas.android.com/apk/res-auto` instead of hardcoded namespace `$value` for Gradle projects",
                    quickfixData = fix
                )
            }
        }
    }
}