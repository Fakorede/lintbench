package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class MotionLayoutDetector : Detector(), XmlScanner {

    companion object {
        const val KEY_URL = "url"

        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "MotionLayout missing or invalid scene file reference",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                The `layoutDescription` attribute is required to specify a valid motion scene file.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            "androidx.constraintlayout.motion.widget.MotionLayout",
            "com.android.support.constraint.motion.MotionLayout"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = element.getAttributeNodeNS(SdkConstants.AUTO_URI, "layoutDescription")
        if (attr == null) {
            val incident = Incident(ISSUE)
                .location(context.getNameLocation(element))
                .message("MotionLayout requires a `layoutDescription` attribute pointing to a motion scene XML file")
                .scope(element)
            context.report(incident)
            return
        }

        val value = attr.value
        val url = ResourceUrl.parse(value)
        val isValid = url != null && url.type == ResourceType.XML && !url.isFramework

        if (!isValid) {
            val map = LintMap().apply { put(KEY_URL, value) }
            val incident = Incident(ISSUE)
                .location(context.getValueLocation(attr))
                .message("The `layoutDescription` attribute must specify a valid motion scene file (e.g., `@xml/scene_file`)")
                .scope(attr)
                .data(map)
            context.report(incident)
        }
    }
}