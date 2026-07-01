package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.SdkConstants
import org.w3c.dom.Element

class MotionLayoutDetector : LayoutDetector() {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "Invalid MotionLayout Scene File Reference",
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

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            "androidx.constraintlayout.motion.widget.MotionLayout",
            "MotionLayout"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = element.getAttributeNodeNS(SdkConstants.AUTO_URI, "layoutDescription")
        if (attr == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "MotionLayout requires a `layoutDescription` attribute"
            )
            return
        }

        val value = attr.value
        if (!value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "The `layoutDescription` attribute must point to an XML resource (`@xml/...`)"
            )
        }
    }
}