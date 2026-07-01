package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.EnumSet

class RestrictionsDetector : ResourceXmlDetector() {

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
        if (element.parentNode?.nodeType != Node.DOCUMENT_NODE) {
            report(context, element, "The `<restrictions>` element must be the root element")
            return
        }

        val keys = mutableSetOf<String>()
        for (child in element.children()) {
            if (child.tagName != TAG_RESTRICTION) {
                report(context, child, "A `<restrictions>` element can only contain `<restriction>` elements")
                continue
            }

            val key = child.getAttributeNS(ANDROID_URI, ATTR_KEY)
            if (key.isNotBlank() && !keys.add(key)) {
                report(context, child, "Duplicate restriction key `$key`")
            }
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element) {
        val keyAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_KEY)
        if (keyAttr == null || keyAttr.value.isBlank()) {
            report(context, element, "A `<restriction>` element must specify an `android:key` attribute")
        }

        val typeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
        val type = typeAttr?.value
        if (type.isNullOrBlank()) {
            report(context, element, "A `<restriction>` element must specify an `android:restrictionType` attribute")
            return
        }

        if (type !in VALID_TYPES) {
            report(
                context, element,
                "Invalid restriction type `$type`; must be one of: ${VALID_TYPES.joinToString()}"
            )
        }

        val titleAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_TITLE)
        if ((titleAttr == null || titleAttr.value.isBlank()) && type != TYPE_HIDDEN) {
            report(context, element, "A `<restriction>` element must specify an `android:title` attribute unless it is hidden")
        }

        when (type) {
            TYPE_CHOICE, TYPE_MULTI_SELECT -> {
                if (element.getAttributeNS(ANDROID_URI, ATTR_ENTRIES).isBlank()) {
                    report(context, element, "A `$type` restriction must specify `android:entries`")
                }
                if (element.getAttributeNS(ANDROID_URI, ATTR_ENTRY_VALUES).isBlank()) {
                    report(context, element, "A `$type` restriction must specify `android:entryValues`")
                }
            }
            TYPE_BUNDLE, TYPE_BUNDLE_ARRAY -> {
                val children = element.children()
                if (children.isEmpty()) {
                    report(context, element, "A `$type` restriction must contain at least one nested `<restriction>`")
                } else {
                    for (child in children) {
                        if (child.tagName != TAG_RESTRICTION) {
                            report(context, child, "A `$type` restriction can only contain `<restriction>` elements")
                        }
                    }
                }
            }
        }

        val defaultValue = element.getAttributeNS(ANDROID_URI, ATTR_DEFAULT_VALUE)
        if (defaultValue.isNotBlank()) {
            when (type) {
                TYPE_BOOL -> if (defaultValue != "true" && defaultValue != "false") {
                    report(context, element, "A `bool` restriction's `android:defaultValue` must be `true` or `false`")
                }
                TYPE_INTEGER -> if (defaultValue.toIntOrNull() == null) {
                    report(context, element, "An `integer` restriction's `android:defaultValue` must be an integer")
                }
                TYPE_BUNDLE, TYPE_BUNDLE_ARRAY -> report(
                    context, element,
                    "A `$type` restriction should not specify `android:defaultValue`"
                )
            }
        }
    }

    private fun report(context: XmlContext, element: Element, message: String) {
        context.report(ISSUE, element, context.getLocation(element), message)
    }

    private fun Element.children(): List<Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"
        private const val ATTR_DEFAULT_VALUE = "defaultValue"

        private const val TYPE_BOOL = "bool"
        private const val TYPE_STRING = "string"
        private const val TYPE_INTEGER = "integer"
        private const val TYPE_CHOICE = "choice"
        private const val TYPE_MULTI_SELECT = "multi_select"
        private const val TYPE_HIDDEN = "hidden"
        private const val TYPE_BUNDLE = "bundle"
        private const val TYPE_BUNDLE_ARRAY = "bundle_array"

        private val VALID_TYPES = setOf(
            TYPE_BOOL, TYPE_STRING, TYPE_INTEGER, TYPE_CHOICE,
            TYPE_MULTI_SELECT, TYPE_HIDDEN, TYPE_BUNDLE, TYPE_BUNDLE_ARRAY
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "The application restrictions XML resource must follow the schema expected by " +
                    "android.content.RestrictionsManager. Each restriction must have a key, title " +
                    "(unless hidden), and a valid restrictionType. Choice and multi-select restrictions " +
                    "must provide entries and entryValues, and bundle/bundle-array restrictions must " +
                    "contain nested restriction elements.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                EnumSet.of(Scope.RESOURCE_FILE)
            )
        )
    }
}