package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import java.util.EnumSet

class MotionLayoutDetector : LayoutDetector() {

    companion object {
        const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"
        private const val MOTION_LAYOUT_FQN = "androidx.constraintlayout.motion.widget.MotionLayout"
        private const val MOTION_LAYOUT_SHORT = "MotionLayout"

        val ISSUE: Issue = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file",
            explanation = "A motion scene file specifies the animations used in a `MotionLayout`. " +
                    "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` is required to specify a valid motion scene file.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                EnumSet.of(Scope.RESOURCE_FILE)
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(MOTION_LAYOUT_FQN, MOTION_LAYOUT_SHORT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = element.getAttributeNode(ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
        if (attr == null || attr.value.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
            )
            return
        }

        val value = attr.value.trim()
        if (!value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                attr,
                context.getLocation(attr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must reference a valid motion scene XML file"
            )
        }
    }
}