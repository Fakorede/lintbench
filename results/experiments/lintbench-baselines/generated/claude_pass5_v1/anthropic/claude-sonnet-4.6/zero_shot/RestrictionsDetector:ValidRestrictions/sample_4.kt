package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
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

/**
 * Detector for validating Android app restrictions XML files.
 *
 * Ensures that restrictions XML files (used with RestrictionsManager) are
 * properly formed according to the Android specification.
 *
 * See: https://developer.android.com/reference/android/content/RestrictionsManager.html
 */
class RestrictionsDetector : ResourceXmlDetector() {

    companion object {
        /** The main issue reported by this detector */
        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an application's restrictions XML file is properly formed.

                The restrictions XML file must have a root element of `<restrictions>`, \
                and each child must be a `<restriction>` element with a valid `android:restrictionType` \
                attribute (one of `bool`, `string`, `integer`, `choice`, `multi-select`, `hidden`, \
                `bundle`, or `bundle_array`).

                Each `<restriction>` must have an `android:key` attribute. Restrictions of type \
                `choice` and `multi-select` must have an `android:entries` and \
                `android:entryValues` attribute. Nested `<restriction>` elements are only allowed \
                inside `bundle` or `bundle_array` type restrictions.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            ),
            moreInfo = "https://developer.android.com/reference/android/content/RestrictionsManager.html"
        )

        // XML element names
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"

        // Attribute names
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"
        private const val ATTR_DEFAULT_VALUE = "defaultValue"
        private const val ATTR_DESCRIPTION = "description"

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

        // Types that support nested restrictions
        private val NESTED_RESTRICTION_TYPES = setOf("bundle", "bundle_array")

        // Types that require entries and entryValues
        private val CHOICE_TYPES = setOf("choice", "multi-select")
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_RESTRICTIONS, TAG_RESTRICTION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_RESTRICTIONS -> checkRestrictionsElement(context, element)
            TAG_RESTRICTION -> checkRestrictionElement(context, element)
        }
    }

    private fun checkRestrictionsElement(context: XmlContext, element: Element) {
        // Check that restrictions element only contains restriction children
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                if (child.tagName != TAG_RESTRICTION) {
                    context.report(
                        ISSUE,
                        child,
                        context.getElementLocation(child),
                        "Unexpected element `<${child.tagName}>` inside `<restrictions>`; expected `<restriction>`"
                    )
                }
            }
        }
    }

    private fun checkRestrictionElement(context: XmlContext, element: Element) {
        val parent = element.parentNode

        // Check that restriction is inside a valid parent
        if (parent is Element) {
            val parentTag = parent.tagName
            if (parentTag != TAG_RESTRICTIONS && parentTag != TAG_RESTRICTION) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "`<restriction>` elements must be nested inside `<restrictions>` or another `<restriction>` of type `bundle` or `bundle_array`"
                )
                return
            }

            // If parent is a restriction, check that it's a bundle or bundle_array type
            if (parentTag == TAG_RESTRICTION) {
                val parentType = parent.getAttributeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
                if (parentType.isNotEmpty() && parentType !in NESTED_RESTRICTION_TYPES) {
                    context.report(
                        ISSUE,
                        element,
                        context.getElementLocation(element),
                        "Nested `<restriction>` elements are only allowed inside `bundle` or `bundle_array` type restrictions, not `$parentType`"
                    )
                }
            }
        }

        // Check for required android:key attribute
        val key = element.getAttributeNS(ANDROID_URI, ATTR_KEY)
        if (key.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Missing required attribute `android:key` in `<restriction>`"
            )
        }

        // Check for required android:restrictionType attribute
        val restrictionType = element.getAttributeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
        if (restrictionType.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Missing required attribute `android:restrictionType` in `<restriction>`"
            )
            return
        }

        // Validate the restriction type value
        if (restrictionType !in VALID_RESTRICTION_TYPES) {
            val location = context.getValueLocation(
                element.getAttributeNodeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
            )
            context.report(
                ISSUE,
                element,
                location,
                "Invalid `android:restrictionType` value `$restrictionType`. Must be one of: ${VALID_RESTRICTION_TYPES.sorted().joinToString(", ")}"
            )
            return
        }

        // For choice and multi-select types, check for required entries and entryValues
        if (restrictionType in CHOICE_TYPES) {
            val entries = element.getAttributeNS(ANDROID_URI, ATTR_ENTRIES)
            if (entries.isEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Restriction of type `$restrictionType` must have an `android:entries` attribute"
                )
            }

            val entryValues = element.getAttributeNS(ANDROID_URI, ATTR_ENTRY_VALUES)
            if (entryValues.isEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Restriction of type `$restrictionType` must have an `android:entryValues` attribute"
                )
            }
        }

        // For bundle and bundle_array types, check that children are restriction elements
        if (restrictionType in NESTED_RESTRICTION_TYPES) {
            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child is Element && child.tagName != TAG_RESTRICTION) {
                    context.report(
                        ISSUE,
                        child,
                        context.getElementLocation(child),
                        "Unexpected element `<${child.tagName}>` inside `$restrictionType` restriction; expected `<restriction>`"
                    )
                }
            }
        }

        // Check that hidden type has a default value
        if (restrictionType == "hidden") {
            val defaultValue = element.getAttributeNS(ANDROID_URI, ATTR_DEFAULT_VALUE)
            if (defaultValue.isEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Restriction of type `hidden` should have an `android:defaultValue` attribute"
                )
            }
        }

        // Validate that the android:name attribute is not used (deprecated in favor of android:key)
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (name.isNotEmpty() && key.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Use `android:key` instead of `android:name` for restriction identifiers"
            )
        }
    }
}