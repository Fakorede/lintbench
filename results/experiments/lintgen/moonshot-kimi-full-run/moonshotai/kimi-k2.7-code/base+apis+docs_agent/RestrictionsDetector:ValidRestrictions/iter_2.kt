package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val TAG_RESTRICTIONS = "restrictions"
private const val TAG_RESTRICTION = "restriction"
private const val ATTR_KEY = "key"
private const val ATTR_RESTRICTION_TYPE = "restrictionType"
private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
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

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.XML

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement
        if (root == null) {
            context.report(
                ISSUE,
                Location.create(context.file),
                "Restrictions XML file is empty"
            )
            return
        }
        if (root.tagName != TAG_RESTRICTIONS) {
            context.report(
                ISSUE,
                root,
                context.getLocation(root),
                "Restrictions XML files must have a root <$TAG_RESTRICTIONS> tag"
            )
        }
    }

    override fun getApplicableElements(): Collection<String> =
        listOf(TAG_RESTRICTIONS, TAG_RESTRICTION)

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_RESTRICTIONS -> validateRestrictions(context, element)
            TAG_RESTRICTION -> validateRestriction(context, element)
        }
    }

    private fun validateRestrictions(context: XmlContext, element: Element) {
        if (element.parentNode?.nodeType != Node.DOCUMENT_NODE) {
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
                "The <$TAG_RESTRICTION> tag must specify a $ATTR_KEY attribute"
            )
        }

        val type = element.getAndroidAttribute(ATTR_RESTRICTION_TYPE)
        if (type.isNullOrBlank()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "The <$TAG_RESTRICTION> tag must specify a $ATTR_RESTRICTION_TYPE attribute"
            )
        } else if (type !in VALID_TYPES) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Invalid restriction type: $type"
            )
        } else {
            validateRestrictionChildren(context, element, type)
        }
    }

    private fun validateRestrictionChildren(
        context: XmlContext,
        element: Element,
        type: String
    ) {
        val isContainer = type == TYPE_BUNDLE || type == TYPE_BUNDLE_ARRAY

        if (!isContainer) {
            for (child in element.childElements) {
                context.report(
                    ISSUE,
                    child,
                    context.getLocation(child),
                    "Only $TYPE_BUNDLE and $TYPE_BUNDLE_ARRAY restrictions can contain nested restrictions"
                )
            }
            return
        }

        var count = 0
        for (child in element.childElements) {
            if (child.tagName != TAG_RESTRICTION) {
                context.report(
                    ISSUE,
                    child,
                    context.getLocation(child),
                    "A $type restriction can only contain <$TAG_RESTRICTION> elements"
                )
                continue
            }

            count++

            if (type == TYPE_BUNDLE_ARRAY) {
                val childType = child.getAndroidAttribute(ATTR_RESTRICTION_TYPE)
                if (!childType.isNullOrBlank() && childType != TYPE_BUNDLE) {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "A $type restriction can only contain <$TAG_RESTRICTION> elements of type $TYPE_BUNDLE"
                    )
                }
            }
        }

        if (count > MAX_NUMBER_OF_NESTED_RESTRICTIONS) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "A $type restriction can contain at most $MAX_NUMBER_OF_NESTED_RESTRICTIONS nested restrictions"
            )
        }

        if (element.restrictionDepth > MAX_NESTING_DEPTH) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Restrictions can only be nested up to $MAX_NESTING_DEPTH levels deep"
            )
        }
    }

    private fun Element.getAndroidAttribute(localName: String): String? {
        val value = getAttributeNS(ANDROID_NS, localName)
        return if (value.isBlank()) null else value
    }

    private val Element.childElements: List<Element>
        get() = (0 until childNodes.length).mapNotNull { index ->
            val node = childNodes.item(index)
            if (node.nodeType == Node.ELEMENT_NODE) node as Element else null
        }

    private val Element.restrictionDepth: Int
        get() {
            var depth = 0
            var node: Node? = this
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
                elements, each with a valid key and restrictionType attribute. Bundle and
                bundle_array restrictions can contain nested restrictions, but no more than
                $MAX_NUMBER_OF_NESTED_RESTRICTIONS and no deeper than $MAX_NESTING_DEPTH levels.
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