package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class MotionLayoutDetector : Detector(), XmlScanner {

    companion object {
        private const val MOTION_LAYOUT_CLASS = "androidx.constraintlayout.motion.widget.MotionLayout"
        private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"
        private const val MOTION_SCENE_RESOURCE_TYPE = "xml"

        @JvmField
        val INVALID_SCENE_FILE_REFERENCE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` is required to specify a valid motion \
                scene file.
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            MOTION_LAYOUT_CLASS,
            // Also handle short name in case it's used with tools:class or similar
            SdkConstants.CLASS_MOTION_LAYOUT ?: MOTION_LAYOUT_CLASS
        ).distinct()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Check if this element is a MotionLayout
        val tagName = element.tagName
        if (tagName != MOTION_LAYOUT_CLASS &&
            tagName != "androidx.constraintlayout.motion.widget.MotionLayout"
        ) {
            return
        }

        // Look for the layoutDescription attribute
        val ns = SdkConstants.AUTO_URI
        val hasAttr = element.hasAttributeNS(ns, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

        if (!hasAttr) {
            // The attribute is missing entirely
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                element,
                context.getNameLocation(element),
                "`MotionLayout` is missing the `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute " +
                        "that specifies a motion scene file"
            )
            return
        }

        val attrValue = element.getAttributeNS(ns, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

        if (attrValue.isNullOrBlank()) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                element,
                context.getValueLocation(
                    element.getAttributeNodeNS(ns, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
                ),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a valid motion scene file"
            )
            return
        }

        // Validate that the reference is to an @xml/ resource
        if (!attrValue.startsWith("@xml/") && !attrValue.startsWith("@+xml/")) {
            // Check if it's a tools: reference or other valid reference
            if (attrValue.startsWith("@")) {
                // It's a resource reference but not to xml/ type
                val attrNode = element.getAttributeNodeNS(ns, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
                context.report(
                    INVALID_SCENE_FILE_REFERENCE,
                    element,
                    context.getValueLocation(attrNode),
                    "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a motion scene file " +
                            "using `@xml/` resource reference, but was `$attrValue`"
                )
            } else {
                val attrNode = element.getAttributeNodeNS(ns, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
                context.report(
                    INVALID_SCENE_FILE_REFERENCE,
                    element,
                    context.getValueLocation(attrNode),
                    "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a valid motion scene file"
                )
            }
        }
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT
    }
}