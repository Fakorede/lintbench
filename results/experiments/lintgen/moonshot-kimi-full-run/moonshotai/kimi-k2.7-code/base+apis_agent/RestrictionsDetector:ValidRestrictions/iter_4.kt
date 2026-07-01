package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : Detector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.XML

    override fun getApplicableElements(): Collection<String>? =
        listOf(TAG_RESTRICTIONS)

    override fun visitElement(context: XmlContext, element: Element) {
        validateContainer(context, element, 0, null)
    }

    private fun validateContainer(
        context: XmlContext,
        container: Element,
        depth: Int,
        expectedChildType: String?
    ) {
        val childDepth = depth + 1
        val children = container.childElements()
        val restrictionCount = children.count { it.tagName == TAG_RESTRICTION }

        if (restrictionCount > MAX_NUMBER_OF_NESTED_RESTRICTIONS) {
            report(
                context,
                container,
                "A `<$TAG_RESTRICTIONS>` element can contain at most $MAX_NUMBER_OF_NESTED_RESTRICTIONS `<$TAG_RESTRICTION>` elements"
            )
        }

        val keys = mutableSetOf<String>()
        for (child in children) {
            if (child.tagName == TAG_RESTRICTION) {
                validateRestriction(context, child, childDepth, keys, expectedChildType)
            } else {
                val message = if (container.tagName == TAG_RESTRICTIONS) {
                    "Only `<$TAG_RESTRICTION>` elements are allowed inside `<$TAG_RESTRICTIONS>`"
                } else {
                    "Only `<$TAG_RESTRICTION>` elements are allowed inside a `<$TAG_RESTRICTION>` of type `bundle` or `bundle_array`"
                }
                report(context, child, message)
            }
        }
    }

    private fun validateRestriction(
        context: XmlContext,
        restriction: Element,
        depth: Int,
        keys: MutableSet<String>,
        expectedType: String?
    ) {
        if (depth > MAX_NESTING_DEPTH) {
            report(
                context,
                restriction,
                "`<$TAG_RESTRICTION>` elements cannot be nested more than $MAX_NESTING_DEPTH levels deep"
            )
            return
        }

        val keyAttr = getAttr(restriction, ATTR_KEY)
        val key = keyAttr?.value
        when {
            key == null || key.isEmpty() ->
                report(context, restriction, "A `<$TAG_RESTRICTION>` must declare a `key` attribute")
            key.startsWith("@") || key.startsWith("?") ->
                report(context, keyAttr, "The `key` attribute must not be a resource reference")
            !keys.add(key) ->
                report(context, keyAttr, "Duplicate restriction key `$key`")
        }

        val typeAttr = getAttr(restriction, ATTR_RESTRICTION_TYPE)
        val type = typeAttr?.value
        when {
            type == null || type.isEmpty() ->
                report(context, restriction, "A `<$TAG_RESTRICTION>` must declare a `restrictionType` attribute")
            type !in VALID_TYPES ->
                report(context, typeAttr, "Invalid `restrictionType` value `$type`")
        }

        if (type != null && type in VALID_TYPES) {
            if (expectedType != null && type != expectedType) {
                report(
                    context,
                    typeAttr ?: restriction,
                    "A `<$TAG_RESTRICTION>` inside a `bundle_array` must be of type `$expectedType`"
                )
            }

            if (type != TYPE_HIDDEN) {
                val titleAttr = getAttr(restriction, ATTR_TITLE)
                val title = titleAttr?.value
                if (title == null || title.isEmpty()) {
                    report(context, restriction, "A `<$TAG_RESTRICTION>` must declare a `title` attribute")
                }
            }

            validateDefaultValue(context, restriction, type)
            validateByType(context, restriction, depth, type)
        }
    }

    private fun validateDefaultValue(context: XmlContext, restriction: Element, type: String) {
        val attr = getAttr(restriction, ATTR_DEFAULT_VALUE) ?: return
        val value = attr.value
        if (value.startsWith("@") || value.startsWith("?")) {
            return
        }
        when (type) {
            TYPE_BOOL -> if (value != "true" && value != "false") {
                report(context, attr, "A `bool` restriction's `defaultValue` must be `true` or `false`")
            }
            TYPE_INTEGER -> if (value.toIntOrNull() == null) {
                report(context, attr, "An `integer` restriction's `defaultValue` must be a valid integer")
            }
            TYPE_BUNDLE, TYPE_BUNDLE_ARRAY -> {
                report(context, attr, "A `$type` restriction cannot have a `defaultValue`")
            }
        }
    }

    private fun validateByType(context: XmlContext, restriction: Element, depth: Int, type: String) {
        when (type) {
            TYPE_BUNDLE, TYPE_BUNDLE_ARRAY -> {
                val children = restriction.childElements().filter { it.tagName == TAG_RESTRICTION }
                if (children.isEmpty()) {
                    report(
                        context,
                        restriction,
                        "A `<$TAG_RESTRICTION>` of type `$type` must contain at least one nested `<$TAG_RESTRICTION>`"
                    )
                }
                validateContainer(
                    context,
                    restriction,
                    depth,
                    if (type == TYPE_BUNDLE_ARRAY) TYPE_BUNDLE else null
                )
            }
            TYPE_CHOICE, TYPE_MULTI_SELECT -> validateChoiceChildren(context, restriction, type)
            else -> validateNoChildren(context, restriction, type)
        }
    }

    private fun validateChoiceChildren(context: XmlContext, restriction: Element, type: String) {
        val children = restriction.childElements()
        var foundChoice = false
        val values = mutableSetOf<String>()

        for (child in children) {
            if (child.tagName != TAG_CHOICE) {
                report(
                    context,
                    child,
                    "Only `<$TAG_CHOICE>` elements are allowed inside a `<$TAG_RESTRICTION>` of type `$type`"
                )
                continue
            }

            foundChoice = true

            val valueAttr = getAttr(child, ATTR_VALUE)
            val value = valueAttr?.value
            when {
                value == null || value.isEmpty() ->
                    report(context, child, "A `<$TAG_CHOICE>` element must declare a `value` attribute")
                !values.add(value) ->
                    report(context, valueAttr, "Duplicate choice value `$value`")
            }

            val titleAttr = getAttr(child, ATTR_TITLE)
            val title = titleAttr?.value
            if (title == null || title.isEmpty()) {
                report(context, child, "A `<$TAG_CHOICE>` element must declare a `title` attribute")
            }
        }

        if (!foundChoice) {
            report(
                context,
                restriction,
                "A `<$TAG_RESTRICTION>` of type `$type` must contain at least one `<$TAG_CHOICE>` element"
            )
        }
    }

    private fun validateNoChildren(context: XmlContext, restriction: Element, type: String) {
        for (child in restriction.childElements()) {
            if (child.tagName == TAG_RESTRICTION) {
                report(
                    context,
                    child,
                    "Nested `<$TAG_RESTRICTION>` elements are only allowed for `bundle` or `bundle_array` restrictions"
                )
            } else {
                report(
                    context,
                    child,
                    "A `<$TAG_RESTRICTION>` of type `$type` must not contain child elements"
                )
            }
        }
    }

    private fun getAttr(element: Element, name: String): Attr? {
        return element.getAttributeNodeNS(ANDROID_URI, name)
            ?: element.getAttributeNode(name)
    }

    private fun Element.childElements(): List<Element> {
        val result = mutableListOf<Element>()
        val children = childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                result.add(child as Element)
            }
        }
        return result
    }

    private fun report(context: XmlContext, node: Node, message: String) {
        context.report(ISSUE, node, context.getLocation(node), message)
    }

    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val TAG_CHOICE = "choice"

        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_DEFAULT_VALUE = "defaultValue"
        private const val ATTR_VALUE = "value"

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

        private const val MAX_NESTING_DEPTH = 20
        private const val MAX_NUMBER_OF_NESTED_RESTRICTIONS = 1000

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid restrictions descriptor",
            explanation = """
                The restrictions XML resource used by android.content.RestrictionsManager must be properly formed.
                Each `<restriction>` element must declare a `key` and a valid `restrictionType`. A `title` is also
                required except for `hidden` restrictions. `choice` and `multi_select` restrictions must contain
                `<choice>` elements with `value` and `title` attributes. Nested `<restriction>` elements are only
                permitted inside `bundle` or `bundle_array` restrictions. A container may contain at most
                $MAX_NUMBER_OF_NESTED_RESTRICTIONS nested restrictions, and the maximum nesting depth is
                $MAX_NESTING_DEPTH.
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