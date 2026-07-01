package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Attr

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

        private const val TAG_STYLE = "style"
        private const val TAG_ITEM = "item"
        private const val ATTR_NAME = "name"
        private const val ANDROID_ID = "android:id"
        private const val ID_PREFIX = "@+id/"
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_STYLE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // We're visiting <style> elements; look at child <item> elements
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == TAG_ITEM) {
                val nameAttr = child.getAttribute(ATTR_NAME)
                if (nameAttr == ANDROID_ID) {
                    val textContent = child.textContent?.trim() ?: continue
                    if (textContent.startsWith(ID_PREFIX)) {
                        val location = context.getLocation(child)
                        context.report(
                            ISSUE,
                            child,
                            location,
                            "This construct can potentially crash `aapt` during a " +
                                "build. Use an explicit `<item type=\"id\" name=\"...\" />` " +
                                "definition instead."
                        )
                    }
                }
            }
        }
    }
}