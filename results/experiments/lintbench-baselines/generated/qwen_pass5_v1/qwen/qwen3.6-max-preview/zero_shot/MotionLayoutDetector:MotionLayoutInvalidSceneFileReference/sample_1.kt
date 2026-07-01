package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr

private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"
private const val MOTION_LAYOUT_OLD = "androidx.constraintlayout.widget.MotionLayout"
private const val MOTION_LAYOUT_NEW = "androidx.constraintlayout.motion.widget.MotionLayout"

class MotionLayoutDetector : LayoutDetector() {
    override fun getApplicableAttributes(): Collection<String>? = listOf(ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val tagName = attribute.ownerElement?.tagName ?: return
        if (tagName != MOTION_LAYOUT_OLD && tagName != MOTION_LAYOUT_NEW) {
            return
        }

        val value = attribute.value
        if (!value.startsWith("@xml/")) {
            context.report(
                issue = ISSUE,
                location = context.getValueLocation(attribute),
                message = "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file (e.g., `@xml/motion_scene`)"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file",
            explanation = "A motion scene file specifies the animations used in a `MotionLayout`. " +
                "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` is required to specify a valid motion scene file.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}