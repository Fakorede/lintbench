package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class MotionLayoutDetector : LayoutDetector() {
    companion object {
        private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"
        private const val MOTION_LAYOUT_SHORT = "MotionLayout"
        private const val MOTION_LAYOUT_FQN = "androidx.constraintlayout.motion.widget.MotionLayout"

        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "\$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION must specify a scene file",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` is required to specify a valid motion scene file.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(MOTION_LAYOUT_SHORT, MOTION_LAYOUT_FQN)

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = element.getAttributeNode(SdkConstants.AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

        if (attr == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute"
            )
            return
        }

        val value = attr.value.trim()
        val isValidSceneReference = value.startsWith("@xml/") || value.startsWith("@+xml/")

        if (!isValidSceneReference) {
            context.report(
                ISSUE,
                attr,
                context.getLocation(attr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must reference a motion scene file in `res/xml/`"
            )
        }
    }
}