package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
import com.android.SdkConstants.AUTO_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

private const val MOTION_LAYOUT = "androidx.constraintlayout.motion.widget.MotionLayout"

class MotionLayoutDetector : LayoutDetector() {

    override fun getApplicableElements(): List<String> = listOf(MOTION_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = element.getAttributeNodeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
        if (attr == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "MotionLayout must specify a motion scene file via `app:layoutDescription`"
            )
            return
        }

        val value = attr.value
        if (value.isBlank() || !value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                attr,
                context.getLocation(attr),
                "`app:layoutDescription` must reference a valid motion scene file (`@xml/...`)"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "MotionLayout must specify a valid scene file reference",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. The \
                `app:layoutDescription` attribute is required to specify a valid motion scene file.
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
}