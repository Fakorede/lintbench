package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
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
        val items = element.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val node = items.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val item = node as Element
                if (item.getAttribute("name") == "android:id") {
                    val value = item.textContent.trim()
                    if (value.startsWith("@+id/")) {
                        context.report(
                            ISSUE,
                            context.getLocation(item),
                            "Defining `android:id` with a dynamically generated ID in a style can cause AAPT to crash. " +
                            "Declare the ID explicitly instead.",
                            fix()
                                .name("Replace with @id/")
                                .replace()
                                .text(value)
                                .with(value.replace("@+id/", "@id/"))
                                .build()
                        )
                    }
                }
            }
        }
    }
}