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
import org.w3c.dom.Element

class NamespaceDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes ?: return
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val value = attr.value ?: continue
            if (!attr.name.startsWith(XMLNS_PREFIX)) continue
            if (value.startsWith(PACKAGE_NS_PREFIX)) {
                val suffix = value.substring(PACKAGE_NS_PREFIX.length)
                if (suffix != ANDROID && suffix != RES_AUTO) {
                    val message = "Hardcoded package in namespace: should use $RES_AUTO_URL instead of $value"
                    context.report(
                        ISSUE,
                        context.getValueLocation(attr),
                        message
                    )
                }
            }
        }
    }

    companion object {
        private const val RES_AUTO = "res-auto"
        private const val ANDROID = "android"
        private const val PACKAGE_NS_PREFIX = "http://schemas.android.com/apk/res/"
        private const val RES_AUTO_URL = "http://schemas.android.com/apk/res-auto"
        private const val XMLNS_PREFIX = "xmlns:"

        const val ISSUE_ID = "ResAuto"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = ISSUE_ID,
            briefDescription = "Hardcoded Package in Namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary; for example,
                you can add a `.debug` package suffix in one version and not the other. Therefore, you
                should not hardcode the application package in the resource; instead, use the special
                namespace `http://schemas.android.com/apk/res-auto` which will cause the tools to figure
                out the right namespace for the resource regardless of the actual package used during the build.
            """.trimIndent(),
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