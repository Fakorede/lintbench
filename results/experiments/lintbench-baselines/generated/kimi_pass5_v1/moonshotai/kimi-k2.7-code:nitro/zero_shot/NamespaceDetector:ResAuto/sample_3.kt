package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlScanner
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : ResourceXmlScanner() {

    override fun getApplicableElements(): Collection<String> = XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        for (i in 0 until element.attributes.length) {
            val attr = element.attributes.item(i) as? Attr ?: continue
            if (attr.namespaceURI != XMLNS_URI) {
                continue
            }
            val value = attr.value ?: continue
            if (value.startsWith(NAMESPACE_PREFIX) &&
                value != RES_AUTO_URI &&
                value != ANDROID_URI
            ) {
                val message = "Hardcoded package namespace found: \"$value\". " +
                    "Use \"$RES_AUTO_URI\" instead."
                context.report(
                    ISSUE,
                    attr,
                    context.getValueLocation(attr),
                    message
                )
            }
        }
    }

    companion object {
        private const val XMLNS_URI = "http://www.w3.org/2000/xmlns/"
        private const val NAMESPACE_PREFIX = "http://schemas.android.com/apk/res/"
        private const val RES_AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        val ISSUE = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary
                (for example, a `.debug` package suffix can be added in one build
                variant and not another). Because of this, you should not hardcode the
                application package in the namespace of an XML resource. Instead, use
                `http://schemas.android.com/apk/res-auto`, which lets the tools resolve
                the correct package automatically.
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