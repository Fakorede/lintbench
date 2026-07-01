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
        private const val ANDROID_ID = "android:id"
        private const val NEW_ID_PREFIX = "@+id/"
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ITEM)
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        // We only care about <item> elements inside <style> blocks
        val parent = element.parentNode ?: return
        if (parent.nodeName != TAG_STYLE) return

        // Check if the item's name is android:id
        val nameAttr = element.getAttribute(ATTR_NAME)
        if (nameAttr != ANDROID_ID) return

        // Check if the value starts with @+id/ (dynamically generated id)
        val value = element.textContent?.trim() ?: return
        if (value.startsWith(NEW_ID_PREFIX)) {
            val fix = fix()
                .name("Declare the id explicitly with <item type=\"id\" name=\"...\" />")
                .replace()
                .text(value)
                .with(value.replace(NEW_ID_PREFIX, "@id/"))
                .build()

            context.report(
                CRASH,
                element,
                context.getValueLocation(element),
                "Defining a style which sets `android:id` to a dynamically generated id " +
                    "(`$value`) can cause many versions of `aapt` to crash. " +
                    "Consider declaring the id explicitly with " +
                    "`<item type=\"id\" name=\"...\" />` instead.",
                fix
            )
        }
    }
}