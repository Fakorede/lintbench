package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_DESCRIPTION
import com.android.SdkConstants.ATTR_KEY
import com.android.SdkConstants.ATTR_TITLE
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
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"
        private const val ATTR_DEFAULT_VALUE = "defaultValue"

        // Valid restriction types
        private val VALID_RESTRICTION_TYPES = setOf(
            "bool",
            "string",
            "integer",
            "choice",
            "multi-select",
            "hidden",
            "bundle",
            "bundle_array"
        )

        // Types that require entries and entryValues
        private val CHOICE_TYPES = setOf("choice", "multi-select")

        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an application's restrictions XML file is properly formed.

                The restrictions XML file must have a `<restrictions>` root element, and each \
                `<restriction>` element must have a valid `android:key`, `android:title`, and \
                `android:restrictionType`. Choice and multi-select restrictions must also \
                include `android:entries` and `android:entryValues` attributes.
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

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        // Check if this looks like a restrictions file
        if (root.tagName != TAG_RESTRICTIONS) {
            return
        }

        // Validate restriction elements
        validateRestrictions(context, root)
    }

    private fun validateRestrictions(context: XmlContext, parent: Element) {
        var child = parent.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                if (element.tagName == TAG_RESTRICTION) {
                    validateRestrictionElement(context, element)
                } else if (element.tagName != TAG_RESTRICTIONS) {
                    context.report(
                        ISSUE,
                        element,
                        context.getElementLocation(element),
                        "Unexpected element `<${element.tagName}>` inside `<${parent.tagName}>`"
                    )
                }
            }
            child = child.nextSibling
        }
    }

    private fun validateRestrictionElement(context: XmlContext, element: Element) {
        // Check for required android:key attribute
        val key = element.getAttributeNS(ANDROID_URI, ATTR_KEY)
        if (key.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Missing required attribute `android:key`"
            )
        } else {
            // Validate key format - should not contain spaces
            if (key.contains(' ')) {
                val attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_KEY)
                context.report(
                    ISSUE,
                    element,
                    if (attr != null) context.getLocation(attr) else context.getElementLocation(element),
                    "Key `$key` must not contain spaces"
                )
            }
        }

        // Check for required android:title attribute
        val title = element.getAttributeNS(ANDROID_URI, ATTR_TITLE)
        if (title.isNullOrEmpty()) {
            // hidden type doesn't require title
            val restrictionType = element.getAttributeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
            if (restrictionType != "hidden") {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Missing required attribute `android:title`"
                )
            }
        }

        // Check for required android:restrictionType attribute
        val restrictionType = element.getAttributeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
        if (restrictionType.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Missing required attribute `android:restrictionType`"
            )
        } else {
            // Validate restriction type value
            if (!VALID_RESTRICTION_TYPES.contains(restrictionType)) {
                val attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
                context.report(
                    ISSUE,
                    element,
                    if (attr != null) context.getLocation(attr) else context.getElementLocation(element),
                    "Invalid `android:restrictionType` value `$restrictionType`. " +
                            "Must be one of: ${VALID_RESTRICTION_TYPES.sorted().joinToString(", ")}"
                )
            } else if (CHOICE_TYPES.contains(restrictionType)) {
                // choice and multi-select require entries and entryValues
                val entries = element.getAttributeNS(ANDROID_URI, ATTR_ENTRIES)
                if (entries.isNullOrEmpty()) {
                    context.report(
                        ISSUE,
                        element,
                        context.getElementLocation(element),
                        "Restriction type `$restrictionType` requires attribute `android:entries`"
                    )
                }

                val entryValues = element.getAttributeNS(ANDROID_URI, ATTR_ENTRY_VALUES)
                if (entryValues.isNullOrEmpty()) {
                    context.report(
                        ISSUE,
                        element,
                        context.getElementLocation(element),
                        "Restriction type `$restrictionType` requires attribute `android:entryValues`"
                    )
                }
            } else if (restrictionType == "bundle" || restrictionType == "bundle_array") {
                // bundle and bundle_array should contain nested restrictions
                validateRestrictions(context, element)
            }
        }

        // Validate defaultValue if present
        val defaultValue = element.getAttributeNS(ANDROID_URI, ATTR_DEFAULT_VALUE)
        if (!defaultValue.isNullOrEmpty()) {
            val restrictionTypeForDefault = element.getAttributeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
            when (restrictionTypeForDefault) {
                "integer" -> {
                    try {
                        defaultValue.toInt()
                    } catch (e: NumberFormatException) {
                        val attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_DEFAULT_VALUE)
                        context.report(
                            ISSUE,
                            element,
                            if (attr != null) context.getLocation(attr) else context.getElementLocation(element),
                            "Invalid default value `$defaultValue` for type `integer`: must be an integer"
                        )
                    }
                }
                "bool" -> {
                    if (defaultValue != "true" && defaultValue != "false") {
                        val attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_DEFAULT_VALUE)
                        context.report(
                            ISSUE,
                            element,
                            if (attr != null) context.getLocation(attr) else context.getElementLocation(element),
                            "Invalid default value `$defaultValue` for type `bool`: must be `true` or `false`"
                        )
                    }
                }
            }
        }
    }
}