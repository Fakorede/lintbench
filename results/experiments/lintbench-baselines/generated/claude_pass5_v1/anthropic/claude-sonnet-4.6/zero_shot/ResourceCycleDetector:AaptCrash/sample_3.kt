package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Attr

class ResourceCycleDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val CRASH = Issue.create(
            id = "AaptCrash",
            briefDescription = "Potential AAPT crash",
            explanation = """
                Defining a style which sets `android:id` to a dynamically generated id can \
                cause many versions of `aapt`, the resource packaging tool, to crash. To work \
                around this, declare the id explicitly with `<item type="id" name="..." />` instead.
                """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("item")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // We're looking for <item> elements inside <style> that set android:id
        // to a dynamically generated id (i.e., @+id/...)
        val parentNode = element.parentNode as? Element ?: return
        if (parentNode.tagName != "style") return

        // Check if this item sets android:id
        val nameAttr = element.getAttribute("name")
        if (nameAttr != "android:id") return

        // Check the value - if it's a dynamically generated id (@+id/...)
        val textContent = element.textContent?.trim() ?: return
        if (textContent.startsWith("@+id/")) {
            val location = context.getElementLocation(element)
            context.report(
                CRASH,
                element,
                location,
                "This may cause a crash in `aapt`. Use `@id/` instead of `@+id/` for the value, " +
                        "and define the id explicitly using `<item type=\"id\" name=\"...\"/>` instead."
            )
        }
    }
}