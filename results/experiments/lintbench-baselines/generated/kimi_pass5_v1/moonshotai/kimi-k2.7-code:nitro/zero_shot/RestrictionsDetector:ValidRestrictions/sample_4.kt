package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : ResourceXmlDetector() {

    private val keys = mutableMapOf<String, Location>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_RESTRICTIONS, TAG_RESTRICTION)
    }

    override fun beforeCheckFile(context: XmlContext) {
        super.beforeCheckFile(context)
        keys.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_RESTRICTIONS -> checkRestrictionsRoot(context, element)
            TAG_RESTRICTION -> checkRestriction(context, element)
        }
    }

    private fun checkRestrictionsRoot(context: XmlContext, element: Element) {
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                if (childElement.tagName != TAG_RESTRICTION) {
                    context.report(
                        ISSUE,
                        childElement,
                        context.getLocation(childElement),
                        "Only `<restriction>` elements are allowed inside `<restrictions>`"
                    )
                }
            }
            child = child.nextSibling
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element
        when (parent?.tagName) {
            TAG_RESTRICTIONS -> {
                // Top-level restriction: valid.
            }
            TAG_RESTRICTION -> {
                val parentType = getRestrictionType(parent)
                if (parentType != TYPE_BUNDLE && parentType != TYPE_BUNDLE_ARRAY) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Restrictions can only be nested inside a `bundle` or `bundle_array` restriction"
                    )
                }
                if (parentType == TYPE_BUNDLE_ARRAY && getRestrictionType(element) != TYPE_BUNDLE) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "A `bundle_array` restriction can only contain `bundle` restrictions"
                    )
                }
            }
            else -> {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "`<restriction>` must be inside `<restrictions>` or a `bundle`/`bundle_array` restriction"
                )
            }
        }

        val key = getAttr(element, ATTR_KEY)
        if (key.isNullOrBlank()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Restriction is missing a `key` attribute"
            )
        } else {
            val previous = keys[key]
            if (previous != null) {
                val location = context.getLocation(element)
                context.report(
                    ISSUE,
                    element,
                    location.withSecondary(previous, "Previous use of `$key`"),
                    "Duplicate restriction key `$key`"
                )
            } else {
                keys[key] = context.getLocation(element)
            }
        }

        if (getAttr(element, ATTR_TITLE).isNullOrBlank()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Restriction is missing a `title` attribute"
            )
        }

        val type = getRestrictionType(element)
        if (type.isNullOrBlank()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Restriction is missing a `restrictionType` attribute"
            )
            return
        }

        if (type !in VALID_TYPES) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Unknown restriction type `$type`"
            )
            return
        }

        when (type) {
            TYPE_CHOICE -> {
                if (!hasAttr(element, ATTR_ENTRIES)) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "A `choice` restriction must specify an `entries` attribute"
                    )
                }
                if (!hasAttr(element, ATTR_ENTRY_VALUES)) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "A `choice` restriction must specify an `entryValues` attribute"
                    )
                }
            }
            TYPE_BUNDLE, TYPE_BUNDLE_ARRAY -> {
                var child = element.firstChild
                while (child != null) {
                    if (child.nodeType == Node.ELEMENT_NODE) {
                        val childElement = child as Element
                        if (childElement.tagName != TAG_RESTRICTION) {
                            context.report(
                                ISSUE,
                                childElement,
                                context.getLocation(childElement),
                                "A `$type` restriction can only contain `<restriction>` elements"
                            )
                        }
                    }
                    child = child.nextSibling
                }
            }
        }
    }

    private fun getRestrictionType(element: Element): String? {
        return getAttr(element, ATTR_TYPE)
    }

    private fun getAttr(element: Element, name: String): String? {
        if (element.hasAttributeNS(ANDROID_URI, name)) {
            val value = element.getAttributeNS(ANDROID_URI, name)
            return if (value.isNotBlank()) value else null
        }
        if (element.hasAttribute(name)) {
            val value = element.getAttribute(name)
            return if (value.isNotBlank()) value else null
        }
        return null
    }

    private fun hasAttr(element: Element, name: String): Boolean {
        return element.hasAttributeNS(ANDROID_URI, name) || element.hasAttribute(name)
    }

    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"

        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_TYPE = "restrictionType"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"

        private const val TYPE_BOOL = "bool"
        private const val TYPE_STRING = "string"
        private const val TYPE_INTEGER = "integer"
        private const val TYPE_CHOICE = "choice"
        private const val TYPE_HIDDEN = "hidden"
        private const val TYPE_BUNDLE = "bundle"
        private const val TYPE_BUNDLE_ARRAY = "bundle_array"

        private val VALID_TYPES = setOf(
            TYPE_BOOL,
            TYPE_STRING,
            TYPE_INTEGER,
            TYPE_CHOICE,
            TYPE_HIDDEN,
            TYPE_BUNDLE,
            TYPE_BUNDLE_ARRAY
        )

        private val IMPLEMENTATION = Implementation(
            RestrictionsDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid restrictions descriptor",
            explanation = """
                The application restrictions XML file must be correctly structured. The root \
                element must be `<restrictions>`, and each child must be a `<restriction>` \
                with a `key`, `title`, and `restrictionType` attribute. The `restrictionType` \
                must be one of the supported values, and types such as `choice` require \
                additional attributes, while `bundle` and `bundle_array` must contain nested \
                restrictions. See `RestrictionsManager` for the complete schema.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )
    }
}