package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ResourceCycleDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AaptCrash",
            briefDescription = "Potential AAPT crash",
            explanation = """
                Defining a style which sets `android:id` to a dynamically generated id can \
                cause many versions of `aapt`, the resource packaging tool, to crash. \
                To work around this, declare the id explicitly with \
                `<item type="id" name="..." />` instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun beforeCheckRootProject(context: Context) {
        // No-op
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("item")
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return null
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.parentNode?.nodeName == "style") {
            val name = element.getAttribute("name")
            if (name == "android:id") {
                val text = element.textContent?.trim() ?: ""
                if (text.startsWith("@+id/")) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Avoid defining `android:id` to a dynamically generated ID (`@+id/`) inside a style to prevent AAPT crashes"
                    )
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // No-op
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // No-op
    }
}