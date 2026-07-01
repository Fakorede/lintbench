package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.SdkConstants.AUTO_URI
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class MotionLayoutDetector : LayoutDetector() {

    override fun getApplicableElements(): List<String> =
        listOf(
            "MotionLayout",
            "android.support.constraint.motion.MotionLayout",
            "androidx.constraintlayout.motion.widget.MotionLayout"
        )

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = element.getAttributeNodeNS(AUTO_URI, SdkConstants.ATTR_LAYOUT_DESCRIPTION)
        val value = attr?.value?.trim().orEmpty()

        if (value.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "A `MotionLayout` must specify a motion scene file using `app:layoutDescription`"
            )
            return
        }

        val url = ResourceUrl.parse(value)
        if (url == null || url.type != ResourceType.XML || url.name.isEmpty()) {
            context.report(
                ISSUE,
                attr ?: element,
                context.getLocation(attr ?: element),
                "`app:layoutDescription` must reference a valid motion scene XML resource (`@xml/...`)"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "`MotionLayout` must specify a scene file",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                The `app:layoutDescription` attribute is required and must reference a valid \
                motion scene XML resource.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}