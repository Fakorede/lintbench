package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class ResourceCycleDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? {
        return listOf("item")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.resourceFolderType != ResourceFolderType.VALUES) {
            return
        }

        val parent = element.parentNode as? Element ?: return
        if (parent.tagName != "style") {
            return
        }

        val name = element.getAttribute("name")
        if (name == "android:id") {
            val value = element.textContent?.trim() ?: return
            if (value.startsWith("@+id/")) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Potential AAPT crash: do not define dynamically generated ids (`@+id/`) in style declarations"
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AaptCrash",
            briefDescription = "Potential AAPT crash",
            explanation = """
                Defining a style which sets `android:id` to a dynamically generated id can \
                cause many versions of `aapt`, the resource packaging tool, to crash. \
                To work around this, declare the id explicitly with `<item type="id" name="..." />` instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}