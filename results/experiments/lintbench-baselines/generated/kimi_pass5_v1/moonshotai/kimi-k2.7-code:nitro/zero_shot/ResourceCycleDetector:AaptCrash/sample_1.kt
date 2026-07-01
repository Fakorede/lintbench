package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class ResourceCycleDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_STYLE)

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE || child.nodeName != TAG_ITEM) {
                continue
            }

            val item = child as Element
            if (item.getAttribute(ATTR_NAME) != ANDROID_ID) {
                continue
            }

            val value = item.textContent?.trim() ?: continue
            if (value.startsWith("@+id/")) {
                context.report(
                    ISSUE,
                    item,
                    context.getLocation(item),
                    "Defining `android:id` with a dynamically generated id in a `<style>` can cause aapt to crash; " +
                        "declare the id explicitly with `<item type=\"id\" name=\"...\" />` instead"
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AaptCrash",
            briefDescription = "Potential AAPT crash",
            explanation = """
                Defining a style which sets `android:id` to a dynamically generated id can cause many versions of `aapt`, the resource packaging tool, to crash. To work around this, declare the id explicitly with `<item type="id" name="..." />` instead.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val TAG_STYLE = "style"
        private const val TAG_ITEM = "item"
        private const val ATTR_NAME = "name"
        private const val ANDROID_ID = "android:id"
    }
}