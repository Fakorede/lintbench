package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : ResourceXmlDetector() {
    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private val VALID_TYPES = setOf(
            "bool", "choice", "multi-select", "string", "integer",
            "hidden", "bundle", "bundle_array"
        )

        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an application's restrictions XML file is properly formed. \
                The root element must be `<restrictions>`, and it must contain `<restriction>` \
                elements with required attributes: `android:key`, `android:title`, and \
                `android:restrictionType`. The `restrictionType` must be one of the valid types \
                defined by the Android framework.
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

    override fun getApplicableElements(): Collection<String>? {
        return listOf(TAG_RESTRICTIONS, TAG_RESTRICTION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.resourceFolderType != ResourceFolderType.XML) return

        when (element.tagName) {
            TAG_RESTRICTIONS -> validateRoot(context, element)
            TAG_RESTRICTION -> validateRestriction(context, element)
        }
    }

    private fun validateRoot(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName != TAG_RESTRICTION) {
                context.report(
                    ISSUE,
                    context.getLocation(child as Element),
                    "Invalid child element `<${child.nodeName}>` in `<restrictions>`. Only `<restriction>` elements are allowed."
                )
            }
        }
    }

    private fun validateRestriction(context: XmlContext, element: Element) {
        var hasKey = false
        var hasTitle = false
        var hasType = false
        var typeAttr: Attr? = null

        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            if (attr.namespaceURI == SdkConstants.ANDROID_URI) {
                when (attr.localName) {
                    "key" -> hasKey = true
                    "title" -> hasTitle = true
                    "restrictionType" -> {
                        hasType = true
                        typeAttr = attr as Attr
                    }
                }
            }
        }

        if (!hasKey) {
            context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:key`")
        }
        if (!hasTitle) {
            context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:title`")
        }
        if (!hasType) {
            context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:restrictionType`")
        } else if (typeAttr != null && typeAttr.nodeValue !in VALID_TYPES) {
            context.report(
                ISSUE,
                context.getLocation(typeAttr),
                "Invalid `android:restrictionType` value `${typeAttr.nodeValue}`. Must be one of: ${VALID_TYPES.joinToString(", ")}"
            )
        }
    }
}