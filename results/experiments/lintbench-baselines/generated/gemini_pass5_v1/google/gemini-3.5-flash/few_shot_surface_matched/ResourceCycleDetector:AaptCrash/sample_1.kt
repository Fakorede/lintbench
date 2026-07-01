package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class ResourceCycleDetector : ResourceXmlDetector(), XmlScanner {

    private val styleParents = mutableMapOf<String, String>()

    override fun beforeCheckRootProject(context: Context) {
        styleParents.clear()
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("item", "style")
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return listOf("parent")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        if (element.tagName == "item") {
            val parent = element.parentNode as? org.w3c.dom.Element
            if (parent != null && parent.tagName == "style") {
                val name = element.getAttribute("name")
                if (name == "android:id") {
                    val value = element.textContent.trim()
                    if (value.startsWith("@+id/")) {
                        context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "Defining a style which sets `android:id` to a dynamically generated id can cause many versions of `aapt`, the resource packaging tool, to crash. To work around this, declare the id explicitly with `<item type=\"id\" name=\"...\" />` instead."
                        )
                    }
                }
            }
        } else if (element.tagName == "style") {
            val name = element.getAttribute("name")
            if (name.isNotEmpty() && !element.hasAttribute("parent")) {
                val lastDot = name.lastIndexOf('.')
                if (lastDot != -1) {
                    val parentName = name.substring(0, lastDot)
                    styleParents[name] = parentName
                }
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        if (attribute.localName == "parent" || attribute.name == "parent") {
            val element = attribute.ownerElement
            if (element.tagName == "style") {
                val name = element.getAttribute("name")
                if (name.isNotEmpty()) {
                    styleParents[name] = attribute.value
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        styleParents.clear()
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AaptCrash",
            briefDescription = "Potential AAPT crash",
            explanation = """
                Defining a style which sets `android:id` to a dynamically generated id can \
                cause many versions of `aapt`, the resource packaging tool, to crash. \
                To work around this, declare the id explicitly with `<item type="id" name="..." />` \
                instead.
                """,
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}