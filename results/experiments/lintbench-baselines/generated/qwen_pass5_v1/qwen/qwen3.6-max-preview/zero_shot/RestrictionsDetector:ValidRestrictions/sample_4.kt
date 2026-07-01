package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import com.android.utils.SdkConstants
import org.w3c.dom.Element

class RestrictionsDetector : ResourceXmlDetector() {

    companion object {
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an applications restrictions XML file is properly formed. " +
                "The root element must be <restrictions>, containing <restriction> children. " +
                "Each <restriction> must define android:key, android:title, and android:restrictionType. " +
                "Nested restrictions are only allowed for bundle and bundle_array types.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"

        private val VALID_TYPES = setOf(
            "bool", "choice", "multi-select", "string", "integer",
            "bundle", "bundle_array", "hidden"
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.XML

    override fun getApplicableElements(): Collection<String> = listOf(TAG_RESTRICTIONS, TAG_RESTRICTION)

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_RESTRICTIONS -> validateRoot(context, element)
            TAG_RESTRICTION -> validateRestriction(context, element)
        }
    }

    private fun validateRoot(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName != TAG_RESTRICTION) {
                context.report(
                    ISSUE,
                    context.getLocation(child),
                    "Invalid child element `<${child.tagName}>` in `<$TAG_RESTRICTIONS>`. Only `<$TAG_RESTRICTION>` is allowed."
                )
            }
        }
    }

    private fun validateRestriction(context: XmlContext, element: Element) {
        val parent = element.parentNode
        val parentTag = if (parent is Element) parent.tagName else null

        if (parentTag != TAG_RESTRICTIONS && parentTag != TAG_RESTRICTION) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "`<$TAG_RESTRICTION>` must be a direct child of `<$TAG_RESTRICTIONS>` or `<$TAG_RESTRICTION>`."
            )
            return
        }

        if (parentTag == TAG_RESTRICTION && parent is Element) {
            val parentType = parent.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_RESTRICTION_TYPE)
            if (parentType != "bundle" && parentType != "bundle_array") {
                context.report(
                    ISSUE,
                    context.getLocation(element),
                    "Nested `<$TAG_RESTRICTION>` elements are only allowed inside `bundle` or `bundle_array` types."
                )
            }
        }

        val key = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_KEY)
        if (key.isEmpty()) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Missing required attribute `android:$ATTR_KEY`."
            )
        }

        val title = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_TITLE)
        if (title.isEmpty()) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Missing required attribute `android:$ATTR_TITLE`."
            )
        }

        val type = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_RESTRICTION_TYPE)
        if (type.isEmpty()) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Missing required attribute `android:$ATTR_RESTRICTION_TYPE`."
            )
        } else if (!VALID_TYPES.contains(type)) {
            val location = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, ATTR_RESTRICTION_TYPE)?.let {
                context.getLocation(it)
            } ?: context.getLocation(element)
            context.report(
                ISSUE,
                location,
                "Invalid `android:$ATTR_RESTRICTION_TYPE` value `$type`. Must be one of: ${VALID_TYPES.joinToString(", ")}."
            )
        }
    }
}