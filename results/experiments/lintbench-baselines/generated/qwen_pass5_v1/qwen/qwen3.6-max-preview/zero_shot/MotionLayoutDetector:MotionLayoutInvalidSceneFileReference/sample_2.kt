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

class MotionLayoutDetector : LayoutDetector() {

    companion object {
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "MotionLayout must specify a valid scene file",
            explanation = "A motion scene file specifies the animations used in a `MotionLayout`. " +
                "The `layoutDescription` attribute is required to specify a valid motion scene file.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("MotionLayout")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = element.getAttributeNode(SdkConstants.AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION) ?: return
        val value = attr.value
        if (!value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "The `layoutDescription` attribute must reference a motion scene file in `res/xml/` (e.g., `@xml/scene_file`)"
            )
        }
    }
}