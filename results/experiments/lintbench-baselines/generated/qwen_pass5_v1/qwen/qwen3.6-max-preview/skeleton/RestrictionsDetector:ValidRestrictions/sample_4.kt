package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element

class RestrictionsDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            RestrictionsDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an application's restrictions XML file is properly formed. " +
                "The root element must be <restrictions>, and each <restriction> child must specify " +
                "android:key, android:title, and android:restrictionType. The restrictionType value " +
                "must be one of the valid types supported by RestrictionsManager.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )

        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private val VALID_RESTRICTION_TYPES = setOf(
            "bool", "choice", "multi-select", "string", "integer", "bundle", "bundle_array"
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.XML

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != "restrictions") return

        val restrictions = document.getElementsByTagName("restriction")
        for (i in 0 until restrictions.length) {
            val element = restrictions.item(i) as? Element ?: continue
            checkRestriction(context, element)
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element) {
        val keyAttr = element.getAttributeNodeNS(ANDROID_URI, "key")
        if (keyAttr == null || keyAttr.value.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required attribute android:key"
            )
        }

        val titleAttr = element.getAttributeNodeNS(ANDROID_URI, "title")
        if (titleAttr == null || titleAttr.value.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required attribute android:title"
            )
        }

        val typeAttr = element.getAttributeNodeNS(ANDROID_URI, "restrictionType")
        if (typeAttr == null || typeAttr.value.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required attribute android:restrictionType"
            )
        } else if (typeAttr.value !in VALID_RESTRICTION_TYPES) {
            context.report(
                ISSUE,
                typeAttr,
                context.getLocation(typeAttr),
                "Invalid android:restrictionType: must be one of ${VALID_RESTRICTION_TYPES.joinToString(", ")}"
            )
        }
    }
}