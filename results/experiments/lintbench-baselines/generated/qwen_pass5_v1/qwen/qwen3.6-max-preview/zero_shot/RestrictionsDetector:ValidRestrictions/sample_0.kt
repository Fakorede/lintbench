package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.EnumSet

class RestrictionsDetector : ResourceXmlDetector() {

    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val TAG_ENTRY = "entry"
        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"

        private val VALID_TYPES = setOf(
            "bool", "string", "integer", "choice", "multi-select",
            "bundle", "bundle_array", "hidden"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an application's restrictions XML file is properly formed. \
                The root element must be `<restrictions>`, containing `<restriction>` elements \
                with required attributes `android:key`, `android:title`, and `android:restrictionType`. \
                Valid types are: bool, string, integer, choice, multi-select, bundle, bundle_array, hidden.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                EnumSet.of(Scope.RESOURCE_FILE)
            )
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.XML

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_RESTRICTIONS)

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                if (childElement.tagName != TAG_RESTRICTION) {
                    context.report(
                        ISSUE,
                        childElement,
                        context.getLocation(childElement),
                        "Expected `<$TAG_RESTRICTION>` tag inside `<$TAG_RESTRICTIONS>`"
                    )
                } else {
                    checkRestriction(context, childElement)
                }
            }
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element) {
        val key = element.getAttributeNS(ANDROID_URI, ATTR_KEY)
        val title = element.getAttributeNS(ANDROID_URI, ATTR_TITLE)
        val type = element.getAttributeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)

        if (key.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element), "Missing required attribute `android:$ATTR_KEY`")
        }
        if (title.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element), "Missing required attribute `android:$ATTR_TITLE`")
        }
        if (type.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element), "Missing required attribute `android:$ATTR_RESTRICTION_TYPE`")
            return
        }
        if (!VALID_TYPES.contains(type)) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Invalid restriction type `$type`. Must be one of: ${VALID_TYPES.joinToString(", ")}"
            )
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                when (type) {
                    "choice", "multi-select" -> {
                        if (childElement.tagName != TAG_ENTRY) {
                            context.report(
                                ISSUE, childElement, context.getLocation(childElement),
                                "Expected `<$TAG_ENTRY>` tag for type `$type`"
                            )
                        } else {
                            checkEntry(context, childElement)
                        }
                    }
                    "bundle" -> {
                        if (childElement.tagName != TAG_RESTRICTION) {
                            context.report(
                                ISSUE, childElement, context.getLocation(childElement),
                                "Expected nested `<$TAG_RESTRICTION>` tag for type `bundle`"
                            )
                        } else {
                            checkRestriction(context, childElement)
                        }
                    }
                    "bundle_array" -> {
                        if (childElement.tagName != TAG_RESTRICTION) {
                            context.report(
                                ISSUE, childElement, context.getLocation(childElement),
                                "Expected `<$TAG_RESTRICTION>` tag for type `bundle_array`"
                            )
                        } else {
                            checkRestriction(context, childElement)
                        }
                    }
                    else -> {
                        context.report(
                            ISSUE, childElement, context.getLocation(childElement),
                            "Unexpected child element `<${childElement.tagName}>` for type `$type`"
                        )
                    }
                }
            }
        }
    }

    private fun checkEntry(context: XmlContext, element: Element) {
        val key = element.getAttributeNS(ANDROID_URI, ATTR_KEY)
        val title = element.getAttributeNS(ANDROID_URI, ATTR_TITLE)

        if (key.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element), "Missing required attribute `android:$ATTR_KEY` on `<$TAG_ENTRY>`")
        }
        if (title.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element), "Missing required attribute `android:$ATTR_TITLE` on `<$TAG_ENTRY>`")
        }
    }
}