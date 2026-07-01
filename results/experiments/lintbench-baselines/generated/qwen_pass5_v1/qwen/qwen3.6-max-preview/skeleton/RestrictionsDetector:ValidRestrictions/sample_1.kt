package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            RestrictionsDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an application's restrictions XML file is properly formed according to the Android RestrictionsManager specification. " +
                    "The root element must be <restrictions>, containing <restriction> elements with required android:key, android:title, and android:restrictionType attributes.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )

        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val TAG_ENTRY = "entry"
        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_TYPE = "restrictionType"
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        private val VALID_TYPES = setOf(
            "bool", "choice", "multi-select", "string", "integer", "bundle", "bundle_array"
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != TAG_RESTRICTIONS) {
            return
        }
        validateRestrictions(context, root)
    }

    private fun validateRestrictions(context: XmlContext, parent: Element) {
        var child = parent.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                if (element.tagName != TAG_RESTRICTION) {
                    context.report(
                        ISSUE,
                        context.getLocation(element),
                        "Expected <restriction> element inside <${parent.tagName}>"
                    )
                } else {
                    validateRestriction(context, element)
                }
            }
            child = child.nextSibling
        }
    }

    private fun validateRestriction(context: XmlContext, element: Element) {
        val key = element.getAttributeNS(ANDROID_NS, ATTR_KEY)
        val title = element.getAttributeNS(ANDROID_NS, ATTR_TITLE)
        val type = element.getAttributeNS(ANDROID_NS, ATTR_TYPE)

        if (key.isEmpty()) {
            context.report(ISSUE, context.getLocation(element), "Missing required attribute android:key")
        }
        if (title.isEmpty()) {
            context.report(ISSUE, context.getLocation(element), "Missing required attribute android:title")
        }
        if (type.isEmpty()) {
            context.report(ISSUE, context.getLocation(element), "Missing required attribute android:restrictionType")
            return
        }
        if (!VALID_TYPES.contains(type)) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Invalid android:restrictionType: $type. Must be one of: ${VALID_TYPES.joinToString()}"
            )
            return
        }

        when (type) {
            "choice", "multi-select" -> validateEntries(context, element)
            "bundle", "bundle_array" -> validateRestrictions(context, element)
            else -> validateNoChildElements(context, element, type)
        }
    }

    private fun validateEntries(context: XmlContext, parent: Element) {
        var child = parent.firstChild
        var hasEntry = false
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                if (element.tagName != TAG_ENTRY) {
                    context.report(
                        ISSUE,
                        context.getLocation(element),
                        "Expected <entry> element inside <restriction> of type choice/multi-select"
                    )
                } else {
                    hasEntry = true
                    val entryKey = element.getAttributeNS(ANDROID_NS, ATTR_KEY)
                    val entryTitle = element.getAttributeNS(ANDROID_NS, ATTR_TITLE)
                    if (entryKey.isEmpty()) {
                        context.report(ISSUE, context.getLocation(element), "Missing required attribute android:key on <entry>")
                    }
                    if (entryTitle.isEmpty()) {
                        context.report(ISSUE, context.getLocation(element), "Missing required attribute android:title on <entry>")
                    }
                }
            }
            child = child.nextSibling
        }
        if (!hasEntry) {
            context.report(
                ISSUE,
                context.getLocation(parent),
                "Restriction of type choice/multi-select must contain at least one <entry>"
            )
        }
    }

    private fun validateNoChildElements(context: XmlContext, parent: Element, type: String) {
        var child = parent.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                context.report(
                    ISSUE,
                    context.getLocation(child as Element),
                    "Element <${child.nodeName}> is not allowed for restrictionType '$type'"
                )
            }
            child = child.nextSibling
        }
    }
}