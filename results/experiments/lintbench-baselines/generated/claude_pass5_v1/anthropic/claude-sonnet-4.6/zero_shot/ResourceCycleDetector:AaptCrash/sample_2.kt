package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.xmlpull.v1.XmlPullParser

class ResourceCycleDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val CRASH = Issue.create(
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
        private const val ANDROID_ID_ATTR = "android:id"
        private const val NEW_ID_PREFIX = "@+id/"
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ITEM)
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        // We're looking for <item> elements inside <style> elements
        val parent = element.parentNode
        if (parent == null || parent.nodeName != TAG_STYLE) {
            return
        }

        // Check if the item's name attribute is "android:id"
        val nameAttr = element.getAttribute(ATTR_NAME)
        if (nameAttr != ANDROID_ID_ATTR) {
            return
        }

        // Check if the value is a dynamically generated id (@+id/...)
        val value = element.textContent?.trim() ?: return
        if (value.startsWith(NEW_ID_PREFIX)) {
            val nameAttrNode = element.getAttributeNode(ATTR_NAME)
            val location = if (nameAttrNode != null) {
                context.getLocation(nameAttrNode)
            } else {
                context.getLocation(element)
            }
            context.report(
                CRASH,
                element,
                location,
                "Avoid setting `android:id` to `@+id/` in styles; this can cause `aapt` to " +
                        "crash. Declare the id explicitly with `<item type=\"id\" name=\"...\" />` instead."
            )
        }
    }
}