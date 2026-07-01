package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

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
                To work around this, declare the id explicitly with `<item type="id" name="..." />` instead.
                """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun beforeCheckRootProject(context: Context) {
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("item")
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return null
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val parent = element.parentNode ?: return
        if (parent.nodeName != "style") {
            return
        }
        val name = element.getAttribute("name")
        if (name == "android:id" || name == "id") {
            val value = element.textContent?.trim() ?: return
            if (value.startsWith("@+id/")) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Avoid defining a style which sets `android:id` to a dynamically generated ID; this can cause AAPT to crash"
                )
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
    }
}