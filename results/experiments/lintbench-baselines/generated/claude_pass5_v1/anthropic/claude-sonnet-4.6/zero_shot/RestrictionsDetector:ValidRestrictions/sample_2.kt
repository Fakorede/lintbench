package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_NAME
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
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"

        private const val ATTR_RESTRICTION_TYPE = "android:restrictionType"
        private const val ATTR_TITLE = "android:title"
        private const val ATTR_DESCRIPTION = "android:description"
        private const val ATTR_DEFAULT_VALUE = "android:defaultValue"
        private const val ATTR_ENTRIES = "android:entries"
        private const val ATTR_ENTRY_VALUES = "android:entryValues"
        private const val ATTR_KEY = "android:key"

        private val VALID_RESTRICTION_TYPES = setOf(
            "bool",
            "choice",
            "integer",
            "multi-select",
            "string",
            "hidden",
            "bundle",
            "bundle_array"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an application's restrictions XML file is properly formed.

                The restrictions XML file must follow specific rules:
                - Each `<restriction>` element must have an `android:key` attribute
                - Each `<restriction>` element must have an `android:restrictionType` attribute
                - The `android:restrictionType` must be one of the valid types: `bool`, `choice`, \
                `integer`, `multi-select`, `string`, `hidden`, `bundle`, or `bundle_array`
                - `choice` and `multi-select` types must have `android:entries` and \
                `android:entryValues` attributes
                - Non-hidden restrictions should have a title
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            moreInfo = "https://developer.android.com/reference/android/content/RestrictionsManager.html",
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_RESTRICTIONS, TAG_RESTRICTION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_RESTRICTIONS -> visitRestrictionsElement(context, element)
            TAG_RESTRICTION -> visitRestrictionElement(context, element)
        }
    }

    private fun visitRestrictionsElement(context: XmlContext, element: Element) {
        // Check that the root element only contains restriction children
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                if (childElement.tagName != TAG_RESTRICTION) {
                    context.report(
                        ISSUE,
                        childElement,
                        context.getLocation(childElement),
                        "Unexpected element `<${childElement.tagName}>` in `<restrictions>` element; " +
                                "expected `<restriction>`"
                    )
                }
            }
            child = child.nextSibling
        }
    }

    private fun visitRestrictionElement(context: XmlContext, element: Element) {
        // Check for required android:key attribute
        val key = element.getAttributeNS(
            "http://schemas.android.com/apk/res/android", "key"
        ).ifEmpty {
            element.getAttribute(ATTR_KEY).ifEmpty { null }
        }

        if (key == null || key.isEmpty()) {
            if (!element.hasAttribute(ATTR_KEY) &&
                !element.hasAttributeNS("http://schemas.android.com/apk/res/android", "key")
            ) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attribute `android:key`"
                )
            }
        }

        // Check for required android:restrictionType attribute
        val restrictionType = getAndroidAttribute(element, "restrictionType")

        if (restrictionType == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required attribute `android:restrictionType`"
            )
            return
        }

        // Validate restrictionType value
        if (restrictionType !in VALID_RESTRICTION_TYPES) {
            val attr = element.getAttributeNodeNS(
                "http://schemas.android.com/apk/res/android", "restrictionType"
            ) ?: element.getAttributeNode(ATTR_RESTRICTION_TYPE)

            val location = if (attr != null) {
                context.getLocation(attr)
            } else {
                context.getLocation(element)
            }

            context.report(
                ISSUE,
                element,
                location,
                "Invalid `android:restrictionType` value `$restrictionType`. " +
                        "Must be one of: ${VALID_RESTRICTION_TYPES.sorted().joinToString(", ")}"
            )
            return
        }

        // For choice and multi-select, entries and entryValues are required
        if (restrictionType == "choice" || restrictionType == "multi-select") {
            val entries = getAndroidAttribute(element, "entries")
            val entryValues = getAndroidAttribute(element, "entryValues")

            if (entries == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attribute `android:entries` for " +
                            "`android:restrictionType=\"$restrictionType\"`"
                )
            }

            if (entryValues == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attribute `android:entryValues` for " +
                            "`android:restrictionType=\"$restrictionType\"`"
                )
            }
        }

        // Check for title (required for non-hidden restrictions)
        if (restrictionType != "hidden" && restrictionType != "bundle" && restrictionType != "bundle_array") {
            val title = getAndroidAttribute(element, "title")
            if (title == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attribute `android:title`"
                )
            }
        }

        // bundle and bundle_array can have nested restriction elements
        if (restrictionType == "bundle" || restrictionType == "bundle_array") {
            var child = element.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val childElement = child as Element
                    if (childElement.tagName != TAG_RESTRICTION) {
                        context.report(
                            ISSUE,
                            childElement,
                            context.getLocation(childElement),
                            "Unexpected element `<${childElement.tagName}>` in `<restriction>` element; " +
                                    "expected `<restriction>`"
                        )
                    }
                }
                child = child.nextSibling
            }
        } else {
            // Non-bundle types should not have child restriction elements
            var child = element.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val childElement = child as Element
                    if (childElement.tagName == TAG_RESTRICTION) {
                        context.report(
                            ISSUE,
                            childElement,
                            context.getLocation(childElement),
                            "Nested `<restriction>` elements are only allowed for " +
                                    "`bundle` and `bundle_array` restriction types"
                        )
                    }
                }
                child = child.nextSibling
            }
        }
    }

    private fun getAndroidAttribute(element: Element, localName: String): String? {
        // Try with namespace first
        val nsValue = element.getAttributeNS(
            "http://schemas.android.com/apk/res/android", localName
        )
        if (nsValue.isNotEmpty()) return nsValue

        // Try with android: prefix
        val prefixedValue = element.getAttribute("android:$localName")
        if (prefixedValue.isNotEmpty()) return prefixedValue

        // Check if attribute exists but is empty
        if (element.hasAttributeNS("http://schemas.android.com/apk/res/android", localName)) {
            return ""
        }
        if (element.hasAttribute("android:$localName")) {
            return ""
        }

        return null
    }
}