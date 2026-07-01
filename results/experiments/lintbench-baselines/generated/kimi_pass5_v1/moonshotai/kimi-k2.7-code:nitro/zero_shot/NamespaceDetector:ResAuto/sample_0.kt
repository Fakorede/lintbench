package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
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

private const val RESOURCE_PACKAGE_PREFIX = "http://schemas.android.com/apk/res/"
private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
private const val RES_AUTO_URI = "http://schemas.android.com/apk/res-auto"

class NamespaceDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun getApplicableAttributes(): Collection<String> = XmlScannerConstants.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        if (attribute.value.trim().isEmpty()) return
        if (!value.startsWith(RESOURCE_PACKAGE_PREFIX) || value == ANDROID_URI) return
        if (attribute.prefix == "xmlns" || attribute.name == "xmlns") {
            val location = context.getValueLocation(attribute)
            val message = "Hardcoded package namespace `$value` found; use `$RES_AUTO_URI` instead"
            val fix = LintFix.create()
                .replace()
                .text(value)
                .with(RES_AUTO_URI)
                .range(location)
                .build()
            context.report(ISSUE, attribute, location, message, fix)
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary;
                for example, you can add a `.debug` package suffix in one version and
                not the other. Therefore, you should not hardcode the application package
                in the resource; instead, use the special namespace
                `http://schemas.android.com/apk/res-auto` which will cause the tools to
                figure out the right namespace for the resource regardless of the actual
                package used during the build.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}