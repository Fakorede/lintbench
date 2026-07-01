package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr

class NamespaceDetector : Detector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun getApplicableAttributes(): Collection<String>? = ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        if (!value.startsWith(NS_PREFIX)) return
        if (value == SdkConstants.AUTO_URI || value == SdkConstants.ANDROID_URI) return

        val name = attribute.name ?: return
        if (!name.startsWith(XMLNS_PREFIX)) return

        val message = "Hardcoded package namespace `$value` found. Use `${SdkConstants.AUTO_URI}` instead."
        context.report(
            ISSUE,
            attribute,
            context.getValueLocation(attribute),
            message
        )
    }

    companion object {
        private const val XMLNS_PREFIX = "xmlns"
        private const val NS_PREFIX = "http://schemas.android.com/apk/res/"

        @JvmField
        val ISSUE = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded package namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary; for example,
                you can add a `.debug` package suffix in one version and not the other. Therefore,
                you should not hardcode the application package in the resource; instead, use the
                special namespace `http://schemas.android.com/apk/res-auto` which will cause the
                tools to figure out the right namespace for the resource regardless of the actual
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
    }
}