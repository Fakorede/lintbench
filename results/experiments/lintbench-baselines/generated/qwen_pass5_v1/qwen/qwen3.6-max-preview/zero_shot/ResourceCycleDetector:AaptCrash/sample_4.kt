package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class ResourceCycleDetector : ResourceXmlDetector() {

    companion object {
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
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("style")

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val item = node as Element
                if ("item" == item.tagName) {
                    checkItem(context, item)
                }
            }
        }
    }

    private fun checkItem(context: XmlContext, item: Element) {
        val nameAttr = item.getAttributeNode("name") ?: return
        val nameValue = nameAttr.value
        if (nameValue == "id" || nameValue.endsWith(":id")) {
            val value = item.textContent?.trim().orEmpty()
            if (value.startsWith("@+id/")) {
                context.report(
                    ISSUE,
                    item,
                    context.getLocation(item),
                    "Defining a style with a dynamically generated id (`@+id/...`) can cause AAPT to crash. " +
                            "Declare the id explicitly in a values file instead."
                )
            }
        }
    }
}