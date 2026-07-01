package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class StartDestinationDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            StartDestinationDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = """
                Every <navigation> element must specify an app:startDestination attribute,
                and the value must reference the android:id of one of its direct child elements.
            """,
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean =
        folderType == com.android.resources.ResourceFolderType.NAVIGATION

    override fun getApplicableElements(): Collection<String>? =
        listOf("navigation")

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val startDestinationAttr = findStartDestinationAttribute(element)

        if (startDestinationAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "No start destination specified",
            )
            return
        }

        val startDestinationId = parseId(startDestinationAttr.value)
        val directChildIds = getDirectChildIds(element)

        if (startDestinationId == null || !directChildIds.contains(startDestinationId)) {
            context.report(
                ISSUE,
                startDestinationAttr,
                context.getLocation(startDestinationAttr),
                "The start destination must reference the android:id of a direct child of this <navigation> element",
            )
        }
    }

    private fun findStartDestinationAttribute(element: org.w3c.dom.Element): org.w3c.dom.Attr? {
        for (i in 0 until element.attributes.length) {
            val attr = element.attributes.item(i) as? org.w3c.dom.Attr ?: continue
            if (attr.localName == "startDestination") {
                return attr
            }
        }
        return null
    }

    private fun getDirectChildIds(element: org.w3c.dom.Element): List<String> {
        val ids = mutableListOf<String>()
        val children = element.childNodes ?: return ids
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != org.w3c.dom.Node.ELEMENT_NODE) continue
            val childElement = child as org.w3c.dom.Element
            val id = findIdAttribute(childElement)?.let { parseId(it.value) }
            if (id != null) ids.add(id)
        }
        return ids
    }

    private fun findIdAttribute(element: org.w3c.dom.Element): org.w3c.dom.Attr? {
        for (i in 0 until element.attributes.length) {
            val attr = element.attributes.item(i) as? org.w3c.dom.Attr ?: continue
            if (attr.localName == "id") {
                return attr
            }
        }
        return null
    }

    private fun parseId(value: String): String? {
        if (!value.startsWith('@')) return null
        val slashIndex = value.indexOf('/')
        if (slashIndex == -1 || slashIndex == value.length - 1) return null
        return value.substring(slashIndex + 1)
    }
}