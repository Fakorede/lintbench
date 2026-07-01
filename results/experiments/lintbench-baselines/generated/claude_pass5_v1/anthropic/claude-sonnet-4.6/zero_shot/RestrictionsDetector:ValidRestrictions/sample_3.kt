package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_APPLICATION
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
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
 * Checks that the restrictions XML file used with RestrictionsManager is
 * properly formed according to the specification.
 */
class RestrictionsDetector : ResourceXmlDetector() {

    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"

        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_TITLE = "title"
        private const val ATTR_DESCRIPTION = "description"
        private const val ATTR_DEFAULT_VALUE = "defaultValue"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"

        private const val TYPE_BOOL = "bool"
        private const val TYPE_CHOICE = "choice"
        private const val TYPE_MULTI_SELECT = "multi-select"
        private const val TYPE_INTEGER = "integer"
        private const val TYPE_STRING = "string"
        private const val TYPE_NULL = "null"
        private const val TYPE_BUNDLE = "bundle"
        private const val TYPE_BUNDLE_ARRAY = "bundle_array"

        private val VALID_TYPES = setOf(
            TYPE_BOOL,
            TYPE_CHOICE,
            TYPE_MULTI_SELECT,
            TYPE_INTEGER,
            TYPE_STRING,
            TYPE_NULL,
            TYPE_BUNDLE,
            TYPE_BUNDLE_ARRAY
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an application's restrictions XML file is properly formed.

                The restrictions XML file must have a `<restrictions>` root tag, and each \
                `<restriction>` element must have a valid `android:restrictionType` attribute, \
                a required `android:key` attribute, and for `choice` and `multi-select` types \
                must include `android:entries` and `android:entryValues` attributes.
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
            TAG_RESTRICTIONS -> checkRestrictionsRoot(context, element)
            TAG_RESTRICTION -> checkRestrictionElement(context, element)
        }
    }

    private fun checkRestrictionsRoot(context: XmlContext, element: Element) {
        // The root element should be <restrictions>
        val parent = element.parentNode
        if (parent != null && parent.nodeName != "#document") {
            // restrictions tag should be the root element
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "The `<restrictions>` element should be the root element of the restrictions file"
            )
        }

        // Check children - only <restriction> elements are valid children
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                if (child.tagName != TAG_RESTRICTION) {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "Unexpected tag `<${child.tagName}>`: only `<restriction>` tags are allowed " +
                                "inside `<restrictions>`"
                    )
                }
            }
        }
    }

    private fun checkRestrictionElement(context: XmlContext, element: Element) {
        // Check that restriction has a parent of either <restrictions> or <restriction>
        val parent = element.parentNode
        if (parent is Element) {
            val parentTag = parent.tagName
            if (parentTag != TAG_RESTRICTIONS && parentTag != TAG_RESTRICTION) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "`<restriction>` elements must be children of `<restrictions>` or " +
                            "another `<restriction>` element (for bundle types)"
                )
            }
        }

        // Check that android:key is present
        val key = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
            .takeIf { it.isNotEmpty() }
            ?: element.getAttributeNS(ANDROID_URI, "key")
                .takeIf { it.isNotEmpty() }

        // android:key is required
        val keyAttr = element.getAttributeNodeNS(ANDROID_URI, "key")
        if (keyAttr == null || keyAttr.value.isBlank()) {
            // Check if the name attribute is being used instead
            val nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
            if (nameAttr == null || nameAttr.value.isBlank()) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attribute `android:key` in `<restriction>`"
                )
            }
        }

        // Check restrictionType
        val restrictionTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
        if (restrictionTypeAttr == null || restrictionTypeAttr.value.isBlank()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required attribute `android:restrictionType` in `<restriction>`"
            )
            return
        }

        val restrictionType = restrictionTypeAttr.value.trim()

        if (restrictionType !in VALID_TYPES) {
            context.report(
                ISSUE,
                element,
                context.getLocation(restrictionTypeAttr),
                "Invalid `android:restrictionType` value `$restrictionType`. " +
                        "Must be one of: ${VALID_TYPES.sorted().joinToString(", ")}"
            )
            return
        }

        // Check title is present for non-null types
        if (restrictionType != TYPE_NULL) {
            val titleAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_TITLE)
            if (titleAttr == null || titleAttr.value.isBlank()) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attribute `android:title` for restriction of type `$restrictionType`"
                )
            }
        }

        // For choice and multi-select, entries and entryValues are required
        if (restrictionType == TYPE_CHOICE || restrictionType == TYPE_MULTI_SELECT) {
            val entriesAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ENTRIES)
            if (entriesAttr == null || entriesAttr.value.isBlank()) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attribute `android:entries` for restriction of type `$restrictionType`"
                )
            }

            val entryValuesAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ENTRY_VALUES)
            if (entryValuesAttr == null || entryValuesAttr.value.isBlank()) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attribute `android:entryValues` for restriction of type `$restrictionType`"
                )
            }
        }

        // For bundle and bundle_array types, validate children
        if (restrictionType == TYPE_BUNDLE || restrictionType == TYPE_BUNDLE_ARRAY) {
            val children = element.childNodes
            var hasChildRestrictions = false
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child is Element) {
                    if (child.tagName != TAG_RESTRICTION) {
                        context.report(
                            ISSUE,
                            child,
                            context.getLocation(child),
                            "Unexpected tag `<${child.tagName}>`: only `<restriction>` tags are " +
                                    "allowed inside a bundle restriction"
                        )
                    } else {
                        hasChildRestrictions = true
                    }
                }
            }
        }

        // For non-bundle types, restriction should not have child restrictions
        if (restrictionType != TYPE_BUNDLE && restrictionType != TYPE_BUNDLE_ARRAY) {
            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child is Element && child.tagName == TAG_RESTRICTION) {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "Nested `<restriction>` elements are only allowed for " +
                                "`bundle` and `bundle_array` restriction types"
                    )
                    break
                }
            }
        }
    }
}