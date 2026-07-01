package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : Detector(), Detector.XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.XML

    override fun getApplicableElements(): Collection<String> =
        listOf(TAG_RESTRICTIONS, TAG_RESTRICTION)

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_RESTRICTIONS -> checkRestrictions(context, element)
            TAG_RESTRICTION -> checkRestriction(context, element)
        }
    }

    private fun checkRestrictions(context: XmlContext, element: Element) {
        if (element != element.ownerDocument.documentElement) {
            return
        }
        val keys = mutableSetOf<String>()
        for (child in element.childElements) {
            if (child.tagName != TAG_RESTRICTION) {
                context.report(
                    ISSUE,
                    child,
                    context.getLocation(child),
                    "Unexpected element `<${child.tagName}>` under `<restrictions>`; expected `<restriction>`"
                )
                continue
            }
            val key = child.attr(ATTR_KEY)
            if (key.isNotEmpty() && !keys.add(key)) {
                context.report(
                    ISSUE,
                    child,
                    context.getLocation(child),
                    "Duplicate restriction key `$key`"
                )
            }
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element) {
        if (element == element.ownerDocument.documentElement) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Restrictions must be defined in a `<restrictions>` root element"
            )
            return
        }

        val depth = computeDepth(element)
        if (depth > MAX_NESTING_DEPTH) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Restrictions can only be nested up to a depth of $MAX_NESTING_DEPTH"
            )
            return
        }

        val type = element.attr(ATTR_RESTRICTION_TYPE)
        if (type.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing `android:restrictionType` attribute"
            )
            return
        }
        if (type !in VALID_TYPES) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Invalid restriction type `$type`"
            )
        }

        val key = element.attr(ATTR_KEY)
        if (key.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing `android:key` attribute"
            )
        } else if (key.startsWith("@")) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Restriction key must be a non-localized string, but was `$key`"
            )
        }

        if (type != TYPE_HIDDEN) {
            val title = element.attr(ATTR_TITLE)
            if (title.isEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing `android:title` attribute"
                )
            }
        }

        if (type == TYPE_CHOICE || type == TYPE_MULTI_SELECT) {
            if (element.attr(ATTR_ENTRIES).isEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing `android:entries` attribute"
                )
            }
            if (element.attr(ATTR_ENTRY_VALUES).isEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing `android:entryValues` attribute"
                )
            }
        }

        if (type == TYPE_BUNDLE || type == TYPE_BUNDLE_ARRAY) {
            if (element.attr(ATTR_DEFAULT_VALUE).isNotEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "A restriction of type `bundle` or `bundle_array` cannot have a default value"
                )
            }

            var count = 0
            for (child in element.childElements) {
                if (child.tagName != TAG_RESTRICTION) {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "Unexpected element `<${child.tagName}>` under `<restriction>`; expected `<restriction>`"
                    )
                } else {
                    count++
                    if (count > MAX_NUMBER_OF_NESTED_RESTRICTIONS) {
                        context.report(
                            ISSUE,
                            child,
                            context.getLocation(child),
                            "A bundle restriction can contain at most $MAX_NUMBER_OF_NESTED_RESTRICTIONS restrictions"
                        )
                    }
                }
            }
            if (count == 0) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "A restriction of type `$type` must contain at least one nested `<restriction>` element"
                )
            }
        } else {
            for (child in element.childElements) {
                context.report(
                    ISSUE,
                    child,
                    context.getLocation(child),
                    "A restriction of type `$type` cannot contain child elements"
                )
            }
        }
    }

    private fun computeDepth(element: Element): Int {
        var depth = 0
        var current: Node? = element.parentNode
        while (current != null && current.nodeType == Node.ELEMENT_NODE) {
            depth++
            current = current.parentNode
        }
        return depth
    }

    private fun Element.attr(name: String): String {
        val value = getAttributeNS(ANDROID_URI, name)
        return if (value.isNotEmpty()) value else getAttribute(name)
    }

    private val Element.childElements: List<Element>
        get() {
            val result = mutableListOf<Element>()
            val children = childNodes
            for (i in 0 until children.length) {
                val node = children.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    result.add(node as Element)
                }
            }
            return result
        }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "ValidRestrictions",
            "Invalid Restrictions Descriptor",
            "Ensures that an application's restrictions XML file is properly formed.",
            Implementation(RestrictionsDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
            "https://developer.android.com/reference/android/content/RestrictionsManager.html",
            Category.CORRECTNESS,
            6,
            Severity.ERROR
        )

        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"
        private const val ATTR_DEFAULT_VALUE = "defaultValue"
        private const val TYPE_BUNDLE = "bundle"
        private const val TYPE_BUNDLE_ARRAY = "bundle_array"
        private const val TYPE_HIDDEN = "hidden"
        private const val TYPE_CHOICE = "choice"
        private const val TYPE_MULTI_SELECT = "multi_select"

        const val MAX_NESTING_DEPTH = 2
        const val MAX_NUMBER_OF_NESTED_RESTRICTIONS = 2

        private val VALID_TYPES = setOf(
            "bool",
            "string",
            "integer",
            TYPE_CHOICE,
            TYPE_MULTI_SELECT,
            TYPE_HIDDEN,
            TYPE_BUNDLE,
            TYPE_BUNDLE_ARRAY
        )
    }
}