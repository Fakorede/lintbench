package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import java.util.EnumSet

class ResourceCycleDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "AaptCrash",
            briefDescription = "Potential AAPT crash",
            explanation = "Defining a style which sets `android:id` to a dynamically generated id can cause many versions of `aapt`, the resource packaging tool, to crash. To work around this, declare the id explicitly with `<item type=\"id\" name=\"...\" />` instead.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                EnumSet.of(Scope.RESOURCE_FILE)
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("item")

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode
        if (parent == null || parent.nodeName != "style") {
            return
        }

        val nameAttr = element.getAttribute("name")
        if (nameAttr != "android:id") {
            return
        }

        val value = element.textContent?.trim() ?: return
        if (value.startsWith("@+id/")) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Defining a dynamically generated ID (`@+id/...`) in a style can cause AAPT to crash. Declare the ID explicitly in a values file instead."
            )
        }
    }
}