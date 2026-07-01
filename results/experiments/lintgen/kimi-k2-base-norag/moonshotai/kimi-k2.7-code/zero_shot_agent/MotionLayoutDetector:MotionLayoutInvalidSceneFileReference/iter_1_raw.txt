package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"
private const val XML_RESOURCE_REF_PREFIX = "@xml/"
private const val NEW_MOTION_LAYOUT = "androidx.constraintlayout.motion.widget.MotionLayout"
private const val OLD_MOTION_LAYOUT = "android.support.constraint.motion.MotionLayout"

class MotionLayoutDetector : LayoutDetector() {

    override fun getApplicableElements() = listOf(NEW_MOTION_LAYOUT, OLD_MOTION_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = element.getAttributeNodeNS(SdkConstants.AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
        if (attr == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "A `MotionLayout` must specify a motion scene file via `app:$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION`"
            )
            return
        }

        val value = attr.value
        if (value.isBlank() ||
            !value.startsWith(XML_RESOURCE_REF_PREFIX) ||
            value.length <= XML_RESOURCE_REF_PREFIX.length
        ) {
            context.report(
                ISSUE,
                attr,
                context.getLocation(attr),
                "The `app:$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must specify a valid motion scene file (e.g., `@xml/scene`)"
            )
        }
    }

    companion object {
        const val KEY_URL = "https://developer.android.com/training/constraint-layout/motionlayout"

        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "Invalid MotionLayout scene file reference",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`.
                The `app:$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute is required to specify a valid motion scene file.
            """.trimIndent(),
            moreInfo = KEY_URL,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            ),
            androidSpecific = true
        )
    }
}