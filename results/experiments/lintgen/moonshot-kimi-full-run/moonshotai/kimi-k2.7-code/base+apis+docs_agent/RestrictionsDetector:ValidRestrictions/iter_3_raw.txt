package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

private const val TAG_RESTRICTIONS = "restrictions"
private const val TAG_RESTRICTION = "restriction"

private const val ATTR_KEY = "key"
private const val ATTR_RESTRICTION_TYPE = "restrictionType"
private const val ATTR_ENTRIES = "entries"
private const val ATTR_ENTRY_VALUES = "entryValues"
private const val ATTR_DEFAULT_VALUE = "defaultValue"

private const val TYPE_BUNDLE = "bundle"
private const val TYPE_BUNDLE_ARRAY = "bundle_array"

private val VALID_TYPES = setOf(
    "bool",
    "string",
    "integer",
    "choice",
    "hidden",
    TYPE_BUNDLE,
    TYPE_BUNDLE_ARRAY
)

class RestrictionsDetector : Detector(), XmlScanner {

    private val keysByDocument = mutableMapOf<Document, MutableSet<String>>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.XML

    override fun getApplicableElements(): Collection<String> =
        listOf(TAG_RESTRICTIONS, TAG_RESTRICTION)

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != TAG_RESTRICTIONS) {
            context.report(
                ISSUE,
                root,
                context.getLocation(root),
                "Restrictions XML files must have a root <$TAG_RESTRICTIONS> tag"
            )
            return
        }
        keysByDocument[document] = mutableSetOf()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_RESTRICTIONS -> validateRestrictions(context, element)
            TAG_RESTRICTION -> validateRestriction(context, element)
        }
    }

    private fun validateRestrictions(context: XmlContext, element: Element) {
        if (element.parentNode == null || element.parentNode.nodeType != Node.DOCUMENT_NODE) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "The <$TAG_RESTRICTIONS> tag must be the root element"
            )
        }

        for (child in element.childElements) {
            if (child.tagName != TAG_RESTRICTION) {
                context.report(
                    ISSUE,
                    child,
                    context.getLocation(child),
                    "The <$TAG_RESTRICTIONS> tag can only contain <$TAG_RESTRICTION> elements"
                )
            }
        }
    }

    private fun validateRestriction(context: XmlContext, element: Element) {
        val key = element.getAndroidAttribute(ATTR_KEY)
        if (key.isNullOrBlank()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing key attribute"
            )
        } else {
            if (key.startsWith("@")) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Key attribute should not be a resource reference"
                )
            } else {
                val keys = keysByDocument[context.document]
                if (keys != null) {
                    if (key in keys) {
                        context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "Duplicate key: $key"
                        )
                    } else {
                        keys.add(key)
                    }
                }
            }
        }

        val type = element.getAndroidAttribute(ATTR_RESTRICTION_TYPE)
        if (type.isNullOrBlank()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing restrictionType attribute"
            )
            return
        }

        if (type !in VALID_TYPES) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Invalid restriction type: $type"
            )
            return
        }

        if (element.restrictionDepth() > MAX_NESTING_DEPTH) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Restrictions can only be nested up to $MAX_NESTING_DEPTH levels deep"
            )
        }

        when (type) {
            "bool", "string", "integer" -> {
                if (element.childElements.isNotEmpty()) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Only $TYPE_BUNDLE and $TYPE_BUNDLE_ARRAY restrictions can contain nested restrictions"
                    )
                }
            }
            "choice" -> {
                if (!element.hasAndroidAttribute(ATTR_ENTRIES) || !element.hasAndroidAttribute(ATTR_ENTRY_VALUES)) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Choice restrictions must specify both $ATTR_ENTRIES and $ATTR_ENTRY_VALUES attributes"
                    )
                }
                if (element.childElements.isNotEmpty()) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Only $TYPE_BUNDLE and $TYPE_BUNDLE_ARRAY restrictions can contain nested restrictions"
                    )
                }
            }
            "hidden" -> {
                if (!element.hasAndroidAttribute(ATTR_DEFAULT_VALUE)) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Hidden restrictions must specify a $ATTR_DEFAULT_VALUE attribute"
                    )
                }
                if (element.childElements.isNotEmpty()) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Only $TYPE_BUNDLE and $TYPE_BUNDLE_ARRAY restrictions can contain nested restrictions"
                    )
                }
            }
            TYPE_BUNDLE, TYPE_BUNDLE_ARRAY -> validateNestedRestrictions(context, element, type)
        }
    }

    private fun validateNestedRestrictions(
        context: XmlContext,
        element: Element,
        type: String
    ) {
        val childRestrictions = element.childElements.filter { it.tagName == TAG_RESTRICTION }

        for (child in element.childElements) {
            if (child.tagName != TAG_RESTRICTION) {
                context.report(
                    ISSUE,
                    child,
                    context.getLocation(child),
                    "A $type restriction can only contain <$TAG_RESTRICTION> elements"
                )
            }
        }

        if (childRestrictions.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "A $type restriction must contain at least one nested restriction"
            )
            return
        }

        if (childRestrictions.size > MAX_NUMBER_OF_NESTED_RESTRICTIONS) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "A $type restriction can contain at most $MAX_NUMBER_OF_NESTED_RESTRICTIONS nested restrictions"
            )
        }

        if (type == TYPE_BUNDLE_ARRAY) {
            for (child in childRestrictions) {
                val childType = child.getAndroidAttribute(ATTR_RESTRICTION_TYPE)
                if (!childType.isNullOrBlank() && childType != TYPE_BUNDLE) {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "$TYPE_BUNDLE_ARRAY restrictions can only contain $TYPE_BUNDLE restrictions"
                    )
                }
            }
        }
    }

    private fun Element.getAndroidAttribute(localName: String): String? {
        val value = getAttributeNS(ANDROID_URI, localName)
        return if (value.isBlank()) null else value
    }

    private fun Element.hasAndroidAttribute(localName: String): Boolean =
        getAndroidAttribute(localName) != null

    private val Element.childElements: List<Element>
        get() = (0 until childNodes.length).mapNotNull { index ->
            val node = childNodes.item(index)
            if (node.nodeType == Node.ELEMENT_NODE) node as Element else null
        }

    private fun Element.restrictionDepth(): Int {
        var depth = 0
        var node: Node? = parentNode
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                val e = node as Element
                if (e.tagName == TAG_RESTRICTION) {
                    depth++
                }
            }
            node = node.parentNode
        }
        return depth
    }

    companion object {
        const val MAX_NESTING_DEPTH = 2
        const val MAX_NUMBER_OF_NESTED_RESTRICTIONS = 100

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an application's restrictions XML file is properly formed.
                The root element must be <restrictions>, containing only <restriction>
                elements, each with a valid key and restrictionType attribute. Choice
                restrictions must specify entries and entryValues attributes; hidden
                restrictions must specify a defaultValue attribute. Bundle and
                bundle_array restrictions must contain nested restrictions, but no more
                than $MAX_NUMBER_OF_NESTED_RESTRICTIONS and no deeper than
                $MAX_NESTING_DEPTH levels. Keys must be unique and must not be
                resource references.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}