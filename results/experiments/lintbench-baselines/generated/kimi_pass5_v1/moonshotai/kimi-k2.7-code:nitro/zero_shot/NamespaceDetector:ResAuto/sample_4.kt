package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
private const val PKG_URI_PREFIX = "http://schemas.android.com/apk/res/"

class NamespaceDetector : ResourceXmlDetector() {

    override fun getApplicableAttributes(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (!attribute.isNamespaceDeclaration()) return

        val value = attribute.value ?: return
        if (value.startsWith(PKG_URI_PREFIX) && value != ANDROID_URI && value != AUTO_URI) {
            val location = context.getValueLocation(attribute)
            context.report(
                ISSUE,
                attribute,
                location,
                "Hardcoded package namespace found. Use `$AUTO_URI` instead of `$value`."
            )
        }
    }

    private fun Attr.isNamespaceDeclaration(): Boolean =
        name == "xmlns" || name.startsWith("xmlns:")

    companion object {
        val ISSUE = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded package in namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary; for example,
                you can add a `.debug` package suffix in one version and not the other. Therefore,
                you should not hardcode the application package in the resource; instead, use the
                special namespace `$AUTO_URI` which will cause the tools to figure out the right
                namespace for the resource regardless of the actual package used during the build.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}