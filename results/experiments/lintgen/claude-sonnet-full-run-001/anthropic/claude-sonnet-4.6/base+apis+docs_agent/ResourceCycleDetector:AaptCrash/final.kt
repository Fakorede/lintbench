package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class ResourceCycleDetector : Detector(), XmlScanner {

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
                """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val ATTR_NAME = "name"
        private const val TAG_ITEM = "item"
        private const val TAG_STYLE = "style"
        private const val ANDROID_ID_ATTR = "android:id"
        private const val ID_PREFIX = "@+id/"
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_STYLE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // We're visiting <style> elements; look for child <item name="android:id"> elements
        // that set the value to a dynamically generated id (@+id/...)
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == TAG_ITEM) {
                val name = child.getAttribute(ATTR_NAME)
                if (name == ANDROID_ID_ATTR) {
                    val value = child.textContent?.trim() ?: continue
                    if (value.startsWith(ID_PREFIX)) {
                        val location = context.getElementLocation(child)
                        context.report(
                            ISSUE,
                            child,
                            location,
                            "Avoid setting `android:id` to `@+id/` in a style; this can " +
                                "cause `aapt` to crash. Declare the id explicitly with " +
                                "`<item type=\"id\" name=\"${value.removePrefix(ID_PREFIX)}\" />` instead."
                        )
                    }
                }
            }
        }
    }
}