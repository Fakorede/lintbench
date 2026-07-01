package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : Detector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.XML

    override fun getApplicableElements(): Collection<String>? =
        listOf(TAG_RESTRICTIONS)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != TAG_RESTRICTIONS) {
            return
        }

        val keys = mutableSetOf<String>()
        for (child in element.childElements()) {
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
        keys: MutableSet<String>
    ) {
        val keyAttr = getAttr(restriction, ATTR_KEY)
        when {
            keyAttr == null ->
                report(context, restriction, "A `<restriction>` must declare a `key` attribute")
            keyAttr.value.isEmpty() ->
                report(context, keyAttr, "The `key` attribute must not be empty")
            !keys.add(keyAttr.value) ->
                report(context, keyAttr, "Duplicate restriction key `${keyAttr.value}`")
        }

        val titleAttr = getAttr(restriction, ATTR_TITLE)
        when {
            titleAttr == null ->
                report(context, restriction, "A `<restriction>` must declare a `title` attribute")
            titleAttr.value.isEmpty() ->
                report(context, titleAttr, "The `title` attribute must not be empty")
        }

        val typeAttr = getAttr(restriction, ATTR_RESTRICTION_TYPE)
        val type = if (typeAttr == null || typeAttr.value.isEmpty()) {
            report(context, restriction, "A `<restriction>` must declare a `restrictionType` attribute")
            null
        } else if (typeAttr.value !in VALID_TYPES) {
            report(context, typeAttr, "Invalid `restrictionType` value `${typeAttr.value}`")
            null
        } else {
            typeAttr.value
        }

        if (type != null) {
            validateDefaultValue(context, restriction, type)
            when (type) {
                TYPE_BUNDLE, TYPE_BUNDLE_ARRAY ->
                    validateBundleChildren(context, restriction, type)
                TYPE_CHOICE, TYPE_MULTI_SELECT ->
                    validateChoiceChildren(context, restriction, type)
                else ->
                    validateNoChildren(context, restriction)
            }
        }
    }

    private fun validateDefaultValue(
        context: XmlContext,
        restriction: Element,
        type: String
    ) {
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
        }
    }

    private fun validateBundleChildren(
        context: XmlContext,
        restriction: Element,
        type: String
    ) {
        val nestedKeys = mutableSetOf<String>()
        for (child in restriction.childElements()) {
            if (child.tagName != TAG_RESTRICTION) {
                report(context, child, "Only `<restriction>` elements are allowed inside `$type` restrictions")
            } else {
                validateRestriction(context, child, nestedKeys)
            }
        }
    }

    private fun validateChoiceChildren(
        context: XmlContext,
        restriction: Element,
        type: String
    ) {
        val children = restriction.childElements()
        var foundChoice = false

        for (child in children) {
            if (child.tagName != TAG_CHOICE) {
                report(context, child, "Only `<choice>` elements are allowed inside `$type` restrictions")
                continue
            }

            foundChoice = true

            val valueAttr = getAttr(child, ATTR_VALUE)
            if (valueAttr == null || valueAttr.value.isEmpty()) {
                report(context, child, "A `<choice>` element must declare a `value` attribute")
            }

            val titleAttr = getAttr(child, ATTR_TITLE)
            if (titleAttr == null || titleAttr.value.isEmpty()) {
                report(context, child, "A `<choice>` element must declare a `title` attribute")
            }
        }

        if (!foundChoice) {
            report(context, restriction, "A `$type` restriction must contain at least one `<choice>` element")
        }
    }

    private fun validateNoChildren(context: XmlContext, restriction: Element) {
        for (child in restriction.childElements()) {
            if (child.tagName == TAG_RESTRICTION) {
                report(context, child, "Nested `<restriction>` elements are only allowed for `bundle` or `bundle_array` types")
            } else {
                report(context, child, "Unexpected element `<${child.tagName}>`")
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

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid restrictions descriptor",
            explanation = """
                The restrictions XML resource used by android.content.RestrictionsManager must be properly formed.
                Each `<restriction>` element must declare a `key`, a `title`, and a valid `restrictionType`.
                `choice` and `multi_select` restrictions must contain `<choice>` elements with `value` and `title`
                attributes, and nested `<restriction>` elements are only permitted inside `bundle` or `bundle_array`
                restrictions.
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