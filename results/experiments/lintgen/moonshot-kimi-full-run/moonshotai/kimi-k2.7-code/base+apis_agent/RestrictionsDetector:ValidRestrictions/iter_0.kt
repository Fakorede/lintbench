package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf(TAG_RESTRICTIONS)

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.resourceFolderType != ResourceFolderType.XML) {
            return
        }
        if (element.tagName == TAG_RESTRICTIONS) {
            validateRestrictionsRoot(context, element)
        }
    }

    private fun validateRestrictionsRoot(context: XmlContext, root: Element) {
        val keys = mutableSetOf<String>()
        for (child in root.directElementChildren()) {
            if (child.tagName != TAG_RESTRICTION) {
                report(context, child, "Only `<restriction>` elements are allowed inside `<restrictions>`")
            } else {
                validateRestriction(context, child, keys)
            }
        }
    }

    private fun validateRestriction(
        context: XmlContext,
        restriction: Element,
        siblingKeys: MutableSet<String>
    ) {
        // key
        if (!restriction.hasAttribute(ATTR_KEY)) {
            report(context, restriction, "A `<restriction>` must declare a `key` attribute")
        } else {
            val key = restriction.getAttribute(ATTR_KEY)
            val keyAttr = restriction.getAttributeNode(ATTR_KEY)
            if (key.isBlank()) {
                report(context, keyAttr, "The `key` attribute must not be empty")
            } else if (!siblingKeys.add(key)) {
                report(context, keyAttr, "Duplicate restriction key `$key`")
            }
        }

        // title
        if (!restriction.hasAttribute(ATTR_TITLE)) {
            report(context, restriction, "A `<restriction>` must declare a `title` attribute")
        } else if (restriction.getAttribute(ATTR_TITLE).isBlank()) {
            report(context, restriction.getAttributeNode(ATTR_TITLE), "The `title` attribute must not be empty")
        }

        // restrictionType
        val typeAttr = restriction.getAttributeNode(ATTR_RESTRICTION_TYPE)
        val type = if (typeAttr == null) {
            report(context, restriction, "A `<restriction>` must declare a `restrictionType` attribute")
            null
        } else {
            val value = typeAttr.value
            if (value !in VALID_TYPES) {
                report(context, typeAttr, "Invalid `restrictionType` value `$value`")
                null
            } else {
                value
            }
        }

        if (type != null) {
            validateDefaultValue(context, restriction, type)
            validateNestedRestrictions(context, restriction, type)
            if (type == TYPE_CHOICE || type == TYPE_MULTI_SELECT) {
                validateChoices(context, restriction)
            }
        }
    }

    private fun validateDefaultValue(context: XmlContext, restriction: Element, type: String) {
        if (!restriction.hasAttribute(ATTR_DEFAULT_VALUE)) {
            return
        }
        val attr = restriction.getAttributeNode(ATTR_DEFAULT_VALUE)
        val value = attr.value
        when (type) {
            TYPE_BOOL -> if (value != "true" && value != "false") {
                report(context, attr, "A `bool` restriction's `defaultValue` must be `true` or `false`")
            }
            TYPE_INTEGER -> if (value.toIntOrNull() == null) {
                report(context, attr, "An `integer` restriction's `defaultValue` must be a valid integer")
            }
            TYPE_BUNDLE, TYPE_BUNDLE_ARRAY -> {
                report(context, attr, "A `$type` restriction must not declare a `defaultValue`")
            }
        }
    }

    private fun validateNestedRestrictions(context: XmlContext, restriction: Element, type: String) {
        val nested = restriction.directElementChildren(TAG_RESTRICTION)
        if (nested.isNotEmpty() && type != TYPE_BUNDLE && type != TYPE_BUNDLE_ARRAY) {
            report(context, nested.first(), "Nested `<restriction>` elements are only allowed for `bundle` or `bundle_array` types")
        }
        val nestedKeys = mutableSetOf<String>()
        for (child in nested) {
            validateRestriction(context, child, nestedKeys)
        }
    }

    private fun validateChoices(context: XmlContext, restriction: Element) {
        val choices = restriction.directElementChildren(TAG_CHOICES)
        if (choices.isEmpty()) {
            report(context, restriction, "A `${restriction.getAttribute(ATTR_RESTRICTION_TYPE)}` restriction must contain a `<choices>` element")
            return
        }
        if (choices.size > 1) {
            report(context, choices[1], "Only one `<choices>` element is allowed per restriction")
        }

        val choice = choices.first()
        val entries = choice.directElementChildren(TAG_ENTRY)
        val entryValues = choice.directElementChildren(TAG_ENTRY_VALUE)

        if (entries.isEmpty()) {
            report(context, choice, "`<choices>` must contain at least one `<entry>`")
        }
        if (entryValues.isEmpty()) {
            report(context, choice, "`<choices>` must contain at least one `<entryValue>`")
        }
        if (entries.isNotEmpty() && entryValues.isNotEmpty() && entries.size != entryValues.size) {
            report(context, choice, "The number of `<entry>` and `<entryValue>` elements must match")
        }
    }

    private fun report(context: XmlContext, node: Node, message: String) {
        context.report(ISSUE, node, context.getLocation(node), message)
    }

    private fun report(context: XmlContext, attribute: Attr, message: String) {
        context.report(ISSUE, attribute, context.getLocation(attribute), message)
    }

    private fun Element.directElementChildren(tagName: String? = null): List<Element> {
        val result = mutableListOf<Element>()
        val children = this.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                if (tagName == null || element.tagName == tagName) {
                    result.add(element)
                }
            }
        }
        return result
    }

    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val TAG_CHOICES = "choices"
        private const val TAG_ENTRY = "entry"
        private const val TAG_ENTRY_VALUE = "entryValue"

        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
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
            TYPE_BOOL,
            TYPE_STRING,
            TYPE_INTEGER,
            TYPE_CHOICE,
            TYPE_MULTI_SELECT,
            TYPE_HIDDEN,
            TYPE_BUNDLE,
            TYPE_BUNDLE_ARRAY
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid restrictions descriptor",
            explanation = """
                The restrictions XML resource used by android.content.RestrictionsManager must be well formed.
                Each `<restriction>` element must declare a `key`, a `title`, and a valid `restrictionType`.
                Default values must match the declared type, choice restrictions must declare matching
                `<entry>` and `<entryValue>` items, and nested restrictions are only permitted inside
                `bundle` or `bundle_array` restrictions.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}