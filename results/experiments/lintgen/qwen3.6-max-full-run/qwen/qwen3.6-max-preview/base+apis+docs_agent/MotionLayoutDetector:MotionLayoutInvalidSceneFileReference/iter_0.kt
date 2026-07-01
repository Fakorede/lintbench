package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr

class MotionLayoutDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "`layoutDescription` must specify a scene file",
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
    }

    override fun getApplicableAttributes(): Collection<String>? =
        listOf(SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value.trim()
        if (value.isEmpty()) return

        val url = ResourceUrl.parse(value)
        if (url == null || url.type != ResourceType.XML) {
            context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "The `layoutDescription` attribute must reference a valid motion scene file in `res/xml/` (e.g., `@xml/scene_file`)"
            )
        }
    }
}