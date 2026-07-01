package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AaptCrash",
            briefDescription = "Potential AAPT crash",
            explanation = """
                Defining a style which sets `android:id` to a dynamically generated id can \
                cause many versions of `aapt`, the resource packaging tool, to crash. To \
                work around this, declare the id explicitly with `<item type="id" name="..." />` \
                instead.
                """,
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("item")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode
        if (parent != null && parent.nodeName == "style") {
            val name = element.getAttribute("name")
            if (name == "android:id") {
                val value = element.textContent?.trim()
                if (value != null && value.startsWith("@+id/")) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Style should not define a dynamically generated id (`@+id/`); use an explicit id declaration instead"
                    )
                }
            }
        }
    }
}