package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.XML

    override fun getApplicableElements(): Collection<String> =
        listOf(TAG_RESTRICTIONS)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.parentNode?.nodeType != Node.DOCUMENT_NODE) {
            return
        }

        val keys = mutableSetOf<String>()
        val children = element.childElements().toList()

        if (children.none { it.tagName == TAG_RESTRICTION }) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Restrictions file must contain at least one `<restriction>` element"
            )
        }

        for (child in children) {
            when (child.tagName) {
                TAG_RESTRICTION -> checkRestriction(context, child, keys)
                else -> context.report(
                    ISSUE,
                    child,
                    context.getLocation(child),
                    "Unexpected element `<${child.tagName}>` inside `<restrictions>`"
                )
            }
        }
    }

    private fun checkRestriction(
        context: XmlContext,
        restriction: Element,
        keys: MutableSet<String>
    ) {
        val key = restriction.getAndroidAttr(ATTR_KEY)
        if (key == null) {
            reportMissing(context, restriction, ATTR_KEY)
        } else if (!keys.add(key)) {
            context.report(
                ISSUE,
                restriction,
                context.getLocation(restriction),
                "Duplicate restriction key `$key`"
            )
        }

        if (restriction.getAndroidAttr(ATTR_TITLE) == null) {
            reportMissing(context, restriction, ATTR_TITLE)
        }

        val type = restriction.getAndroidAttr(ATTR_RESTRICTION_TYPE)
        if (type == null) {
            reportMissing(context, restriction, ATTR_RESTRICTION_TYPE)
            return
        }

        if (type !in VALID_TYPES) {
            context.report(
                ISSUE,
                restriction,
                context.getLocation(restriction),
                "Invalid restrictionType `$type`; must be one of ${VALID_TYPES.joinToString()}"
            )
            return
        }

        val choices = restriction.childElements().filter { it.tagName == TAG_CHOICE }.toList()
        val nested = restriction.childElements().filter { it.tagName == TAG_RESTRICTION }.toList()

        when (type) {
            TYPE_CHOICE, TYPE_MULTI_SELECT -> {
                if (choices.isEmpty()) {
                    context.report(
                        ISSUE,
                        restriction,
                        context.getLocation(restriction),
                        "Restrictions of type `$type` must contain at least one `<choice>` element"
                    )
                }
                choices.forEach { checkChoice(context, it) }
            }
            else -> {
                if (choices.isNotEmpty()) {
                    context.report(
                        ISSUE,
                        restriction,
                        context.getLocation(restriction),
                        "`<choice>` elements are only allowed for `choice` or `multi_select` restrictions"
                    )
                }
            }
        }

        when (type) {
            TYPE_BUNDLE -> nested.forEach { checkRestriction(context, it, keys) }
            TYPE_BUNDLE_ARRAY -> {
                if (nested.size != 1 || nested[0].getAndroidAttr(ATTR_RESTRICTION_TYPE) != TYPE_BUNDLE) {
                    context.report(
                        ISSUE,
                        restriction,
                        context.getLocation(restriction),
                        "A restriction of type `bundle_array` must contain exactly one nested `bundle` restriction"
                    )
                }
                nested.forEach { checkRestriction(context, it, keys) }
            }
            else -> {
                if (nested.isNotEmpty()) {
                    context.report(
                        ISSUE,
                        restriction,
                        context.getLocation(restriction),
                        "Nested `<restriction>` elements are only allowed for `bundle` or `bundle_array` types"
                    )
                }
            }
        }

        val defaultValue = restriction.getAttributeNS(ANDROID_URI, ATTR_DEFAULT_VALUE)
        if (defaultValue.isNotBlank()) {
            validateDefaultValue(context, restriction, type, defaultValue)
        }
    }

    private fun checkChoice(context: XmlContext, choice: Element) {
        if (choice.getAndroidAttr(ATTR_VALUE) == null) {
            reportMissing(context, choice, ATTR_VALUE)
        }
        if (choice.getAndroidAttr(ATTR_TITLE) == null) {
            reportMissing(context, choice, ATTR_TITLE)
        }
    }

    private fun validateDefaultValue(
        context: XmlContext,
        restriction: Element,
        type: String,
        value: String
    ) {
        when (type) {
            TYPE_BOOL -> {
                if (!isResourceReference(value) && value != "true" && value != "false") {
                    context.report(
                        ISSUE,
                        restriction,
                        context.getLocation(restriction),
                        "Default value for `bool` restriction must be `true` or `false`"
                    )
                }
            }
            TYPE_INTEGER -> {
                if (!isResourceReference(value) && value.toIntOrNull() == null) {
                    context.report(
                        ISSUE,
                        restriction,
                        context.getLocation(restriction),
                        "Default value for `integer` restriction must be a valid integer"
                    )
                }
            }
            TYPE_FLOAT -> {
                if (!isResourceReference(value) && value.toFloatOrNull() == null) {
                    context.report(
                        ISSUE,
                        restriction,
                        context.getLocation(restriction),
                        "Default value for `float` restriction must be a valid float"
                    )
                }
            }
            TYPE_CHOICE -> {
                val choiceValues = restriction.childElements()
                    .filter { it.tagName == TAG_CHOICE }
                    .mapNotNull { it.getAndroidAttr(ATTR_VALUE) }
                    .toSet()
                if (!isResourceReference(value) && value !in choiceValues) {
                    context.report(
                        ISSUE,
                        restriction,
                        context.getLocation(restriction),
                        "Default value for `choice` restriction must match one of the `<choice>` values"
                    )
                }
            }
            TYPE_MULTI_SELECT -> {
                if (!isResourceReference(value)) {
                    context.report(
                        ISSUE,
                        restriction,
                        context.getLocation(restriction),
                        "Default value for `multi_select` restriction must be an array resource reference"
                    )
                }
            }
            TYPE_BUNDLE, TYPE_BUNDLE_ARRAY -> {
                context.report(
                    ISSUE,
                    restriction,
                    context.getLocation(restriction),
                    "Restrictions of type `$type` cannot specify a default value"
                )
            }
        }
    }

    private fun reportMissing(context: XmlContext, element: Element, attr: String) {
        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "Missing required `android:$attr` attribute on `<${element.tagName}>`"
        )
    }

    private fun isResourceReference(value: String): Boolean = value.startsWith("@")

    private fun Element.getAndroidAttr(name: String): String? =
        getAttributeNS(ANDROID_URI, name).takeIf { it.isNotBlank() }

    private fun Element.childElements(): Sequence<Element> = sequence {
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                yield(node as Element)
            }
        }
    }

    companion object {
        private val IMPLEMENTATION = Implementation(
            RestrictionsDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an application's restrictions XML file is properly formed. \
                Restrictions XML files must define a `<restrictions>` root element containing \
                `<restriction>` elements with valid attributes, types, and nested content.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )

        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val TAG_CHOICE = "choice"

        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_VALUE = "value"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_DEFAULT_VALUE = "defaultValue"

        private const val TYPE_BOOL = "bool"
        private const val TYPE_INTEGER = "integer"
        private const val TYPE_FLOAT = "float"
        private const val TYPE_STRING = "string"
        private const val TYPE_CHOICE = "choice"
        private const val TYPE_MULTI_SELECT = "multi_select"
        private const val TYPE_BUNDLE = "bundle"
        private const val TYPE_BUNDLE_ARRAY = "bundle_array"

        private val VALID_TYPES = setOf(
            TYPE_BOOL,
            TYPE_INTEGER,
            TYPE_FLOAT,
            TYPE_STRING,
            TYPE_CHOICE,
            TYPE_MULTI_SELECT,
            TYPE_BUNDLE,
            TYPE_BUNDLE_ARRAY
        )
    }
}