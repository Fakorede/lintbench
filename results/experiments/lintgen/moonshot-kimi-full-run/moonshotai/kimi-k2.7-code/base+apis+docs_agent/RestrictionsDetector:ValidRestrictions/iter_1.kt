package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val TAG_RESTRICTIONS = "restrictions"
private const val TAG_RESTRICTION = "restriction"
private const val ATTR_KEY = "key"
private const val ATTR_TYPE = "type"
private const val ATTR_RESTRICTION_TYPE = "restrictionType"
private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

private val VALID_TYPES = setOf(
    "bool",
    "string",
    "integer",
    "choice",
    "hidden",
    "bundle",
    "bundle_array"
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
        val key = element.getAttributeValue(ATTR_KEY)
        if (key.isNullOrBlank()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "The <$TAG_RESTRICTION> tag must specify a $ATTR_KEY attribute"
            )
        }

        val type = element.getAttributeValue(ATTR_TYPE)
            ?: element.getAttributeValue(ATTR_RESTRICTION_TYPE)
        if (type.isNullOrBlank()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "The <$TAG_RESTRICTION> tag must specify a $ATTR_TYPE (or $ATTR_RESTRICTION_TYPE) attribute"
            )
        } else if (type !in VALID_TYPES) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Invalid restriction type: $type"
            )
        } else if (type == "bundle" || type == "bundle_array") {
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
        }
    }

    private fun Element.getAttributeValue(localName: String): String? {
        val namespaced = getAttributeNS(ANDROID_NS, localName)
        if (namespaced.isNotBlank()) return namespaced

        val unqualified = getAttribute(localName)
        return unqualified.takeIf { it.isNotBlank() }
    }

    private val Element.childElements: List<Element>
        get() = (0 until childNodes.length).mapNotNull { index ->
            val node = childNodes.item(index)
            if (node.nodeType == Node.ELEMENT_NODE) node as Element else null
        }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an application's restrictions XML file is properly formed.
                The root element must be <restrictions>, containing only <restriction>
                elements, each with a valid key and type (or restrictionType) attribute.
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