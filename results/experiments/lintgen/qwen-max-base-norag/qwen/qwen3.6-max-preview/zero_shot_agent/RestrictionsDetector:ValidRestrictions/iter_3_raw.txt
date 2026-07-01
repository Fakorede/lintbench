package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : ResourceXmlDetector() {
    companion object {
        const val TAG_RESTRICTIONS = "restrictions"
        const val TAG_RESTRICTION = "restriction"
        const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        const val ATTR_KEY = "key"
        const val ATTR_TITLE = "title"
        const val ATTR_TYPE = "restrictionType"
        const val ATTR_ENTRIES = "entries"
        const val ATTR_ENTRY_VALUES = "entryValues"
        const val ATTR_DEFAULT_VALUE = "defaultValue"

        const val MAX_NESTING_DEPTH = 10
        const val MAX_NUMBER_OF_NESTED_RESTRICTIONS = 100

        private val VALID_TYPES = setOf(
            "bool", "choice", "multi-select", "string", "integer", "hidden", "bundle", "bundle_array"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an applications restrictions XML file is properly formed",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.XML

    override fun getApplicableElements(): Collection<String>? =
        listOf(TAG_RESTRICTIONS, TAG_RESTRICTION)

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_RESTRICTIONS -> checkRestrictionsRoot(context, element)
            TAG_RESTRICTION -> checkRestriction(context, element)
        }
    }

    private fun checkRestrictionsRoot(context: XmlContext, element: Element) {
        if (element.parentNode != null && element.parentNode.nodeType != Node.DOCUMENT_NODE) {
            context.report(ISSUE, element, context.getLocation(element), "<restrictions> must be the root element")
            return
        }
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && (child as Element).tagName != TAG_RESTRICTION) {
                context.report(ISSUE, child, context.getLocation(child), "Only <restriction> elements are allowed inside <restrictions>")
            }
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element) {
        val key = element.getAttributeNS(ANDROID_URI, ATTR_KEY).orEmpty()
        val title = element.getAttributeNS(ANDROID_URI, ATTR_TITLE).orEmpty()
        val type = element.getAttributeNS(ANDROID_URI, ATTR_TYPE).orEmpty()
        val defaultValue = element.getAttributeNS(ANDROID_URI, ATTR_DEFAULT_VALUE).orEmpty()

        if (key.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element), "Missing required attribute android:key")
        } else if (key.startsWith("@")) {
            context.report(ISSUE, element, context.getLocation(element), "android:key must not be localized")
        }

        if (title.isEmpty() && type != "hidden") {
            context.report(ISSUE, element, context.getLocation(element), "Missing required attribute android:title")
        }

        if (type.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element), "Missing required attribute android:restrictionType")
            return
        }

        if (!VALID_TYPES.contains(type)) {
            context.report(ISSUE, element, context.getLocation(element), "Invalid android:restrictionType: must be one of ${VALID_TYPES.joinToString(", ")}")
            return
        }

        if (type == "choice" || type == "multi-select") {
            if (element.getAttributeNS(ANDROID_URI, ATTR_ENTRIES).orEmpty().isEmpty()) {
                context.report(ISSUE, element, context.getLocation(element), "Missing required attribute android:entries for type $type")
            }
            if (element.getAttributeNS(ANDROID_URI, ATTR_ENTRY_VALUES).orEmpty().isEmpty()) {
                context.report(ISSUE, element, context.getLocation(element), "Missing required attribute android:entryValues for type $type")
            }
        }

        if ((type == "bundle" || type == "bundle_array") && defaultValue.isNotEmpty()) {
            context.report(ISSUE, element, context.getLocation(element), "android:defaultValue is not allowed for type $type")
        } else if (type == "integer" && defaultValue.isNotEmpty()) {
            if (defaultValue.toIntOrNull() == null) {
                context.report(ISSUE, element, context.getLocation(element), "android:defaultValue must be a valid integer")
            }
        } else if (type == "bool" && defaultValue.isNotEmpty()) {
            if (defaultValue != "true" && defaultValue != "false") {
                context.report(ISSUE, element, context.getLocation(element), "android:defaultValue must be true or false")
            }
        }

        var depth = 0
        var parent = element.parentNode
        while (parent != null && parent.nodeType == Node.ELEMENT_NODE) {
            if ((parent as Element).tagName == TAG_RESTRICTION) {
                depth++
            }
            parent = parent.parentNode
        }
        if (depth > MAX_NESTING_DEPTH) {
            context.report(ISSUE, element, context.getLocation(element), "Nesting depth exceeds maximum of $MAX_NESTING_DEPTH")
        }

        val children = element.childNodes
        val childRestrictions = mutableListOf<Element>()
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && (child as Element).tagName == TAG_RESTRICTION) {
                childRestrictions.add(child)
            }
        }

        if (childRestrictions.isNotEmpty()) {
            if (type != "bundle" && type != "bundle_array") {
                context.report(ISSUE, element, context.getLocation(element), "Nested <restriction> elements are only allowed for bundle and bundle_array types")
            }
            if (childRestrictions.size > MAX_NUMBER_OF_NESTED_RESTRICTIONS) {
                context.report(ISSUE, element, context.getLocation(element), "Too many nested restrictions")
            }
        } else {
            if (type == "bundle" || type == "bundle_array") {
                context.report(ISSUE, element, context.getLocation(element), "$type must contain nested <restriction> elements")
            }
        }

        if (key.isNotEmpty()) {
            val parentNode = element.parentNode
            if (parentNode != null && parentNode.nodeType == Node.ELEMENT_NODE) {
                val siblings = parentNode.childNodes
                for (i in 0 until siblings.length) {
                    val sibling = siblings.item(i)
                    if (sibling === element) break
                    if (sibling.nodeType == Node.ELEMENT_NODE && (sibling as Element).tagName == TAG_RESTRICTION) {
                        if (sibling.getAttributeNS(ANDROID_URI, ATTR_KEY).orEmpty() == key) {
                            context.report(ISSUE, element, context.getLocation(element), "Duplicate android:key \"$key\"")
                            break
                        }
                    }
                }
            }
        }
    }
}