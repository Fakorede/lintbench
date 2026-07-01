package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_LAYOUT_DESCRIPTION
import com.android.SdkConstants.AUTO_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class MotionLayoutDetector : LayoutDetector() {

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            "androidx.constraintlayout.motion.widget.MotionLayout",
            "com.android.support.constraint.motion.MotionLayout"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attribute = element.getAttributeNodeNS(AUTO_URI, ATTR_LAYOUT_DESCRIPTION)
        if (attribute == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "MotionLayout requires a `layoutDescription` attribute"
            )
            return
        }

        val value = attribute.value
        if (value.isNullOrEmpty() || !value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "The `layoutDescription` attribute must specify a valid motion scene XML file (e.g., `@xml/scene`)"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "MotionLayout missing or invalid scene file reference",
            explanation = "A motion scene file specifies the animations used in a `MotionLayout`. The `layoutDescription` attribute is required to specify a valid motion scene file.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.LAYOUT_RESOURCE_FILES
            )
        )
    }
}