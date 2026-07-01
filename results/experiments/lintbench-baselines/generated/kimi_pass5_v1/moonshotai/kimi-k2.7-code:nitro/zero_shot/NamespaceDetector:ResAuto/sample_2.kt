package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
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
        for (i in 0 until element.attributes.length) {
            val attr = element.attributes.item(i) as? Attr ?: continue
            if (!attr.name.startsWith(XMLNS_PREFIX)) {
                continue
            }

            val value = attr.value
            if (!value.startsWith(RESOURCE_PACKAGE_PREFIX)) {
                continue
            }

            val pkg = value.substring(RESOURCE_PACKAGE_PREFIX.length)
            if (pkg != PACKAGE_ANDROID && pkg != PACKAGE_AUTO) {
                context.report(
                    ISSUE_RES_AUTO,
                    attr,
                    context.getValueLocation(attr),
                    "Hardcoded package found in resource namespace; use " +
                        "$RESOURCE_AUTO instead"
                )
            }
        }
    }

    override fun getIssues(): List<Issue> = ISSUES

    companion object {
        private const val XMLNS_PREFIX = "xmlns:"
        private const val RESOURCE_PACKAGE_PREFIX = "http://schemas.android.com/apk/res/"
        private const val RESOURCE_AUTO = "http://schemas.android.com/apk/res-auto"
        private const val PACKAGE_ANDROID = "android"
        private const val PACKAGE_AUTO = "auto"

        @JvmStatic
        val ISSUES = listOf(ISSUE_RES_AUTO)

        val ISSUE_RES_AUTO = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary; for example,
                you can add a `.debug` package suffix in one version and not the other. Therefore,
                you should not hardcode the application package in the resource; instead, use the
                special namespace $RESOURCE_AUTO which will cause the tools to figure out the right
                namespace for the resource regardless of the actual package used during the build.
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