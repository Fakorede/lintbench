package com.android.tools.lint.checks

import com.android.SdkConstants.AUTO_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class MotionLayoutDetector : LayoutDetector(), XmlScanner {

    companion object {
        private const val ATTR_LAYOUT_DESCRIPTION = "layoutDescription"

        private const val MOTION_LAYOUT_ANDROIDX = "androidx.constraintlayout.motion.widget.MotionLayout"
        private const val MOTION_LAYOUT_SUPPORT = "com.android.support.constraint.motion.MotionLayout"
        private const val MOTION_LAYOUT_SHORT = "MotionLayout"

        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "MotionLayout missing or invalid scene file reference",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                The `layoutDescription` attribute is required to specify a valid motion scene file.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(MOTION_LAYOUT_ANDROIDX, MOTION_LAYOUT_SUPPORT, MOTION_LAYOUT_SHORT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attribute = if (element.hasAttributeNS(AUTO_URI, ATTR_LAYOUT_DESCRIPTION)) {
            element.getAttributeNodeNS(AUTO_URI, ATTR_LAYOUT_DESCRIPTION)
        } else {
            null
        }

        if (attribute == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "The attribute `app:layoutDescription` is missing"
            )
            return
        }

        val value = attribute.value
        if (value.isNullOrEmpty()) {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "The `app:layoutDescription` attribute cannot be empty"
            )
        } else if (!value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "The `app:layoutDescription` attribute must point to an xml resource (e.g. `@xml/your_scene`)"
            )
        }
    }
}