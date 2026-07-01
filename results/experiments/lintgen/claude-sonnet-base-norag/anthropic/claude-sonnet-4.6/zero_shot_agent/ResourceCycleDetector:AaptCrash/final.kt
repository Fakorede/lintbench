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

        private const val ATTR_NAME = "name"
        private const val TAG_ITEM = "item"
        private const val TAG_STYLE = "style"
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
        // We're looking for <item> elements inside <style> that set android:id to @+id/...
        val parent = element.parentNode ?: return
        if (parent.nodeName != TAG_STYLE) {
            return
        }

        // Check if this item's name attribute is "android:id"
        val nameAttr = element.getAttribute(ATTR_NAME)
        if (nameAttr != ANDROID_ID_ATTR) {
            return
        }

        // Check if the text content starts with @+id/
        val textContent = element.textContent?.trim() ?: return
        if (textContent.startsWith(NEW_ID_PREFIX)) {
            val nameNode = element.getAttributeNode(ATTR_NAME)
            val location = if (nameNode != null) {
                context.getLocation(nameNode)
            } else {
                context.getLocation(element)
            }

            context.report(
                CRASH,
                element,
                location,
                "This construct can potentially crash `aapt` during a build. " +
                    "Change `@+id/${textContent.removePrefix(NEW_ID_PREFIX)}` to `@id/${textContent.removePrefix(NEW_ID_PREFIX)}` " +
                    "and declare the id explicitly using `<item type=\"id\" name=\"${textContent.removePrefix(NEW_ID_PREFIX)}\" />` instead."
            )
        }
    }
}