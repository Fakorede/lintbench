package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceFolderType
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class MotionLayoutDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("MotionLayout", "androidx.constraintlayout.motion.widget.MotionLayout")
    }

    override fun afterCheckRootProject(context: Context) {
        // No project-level aggregation required for this check
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = element.getAttributeNodeNS(
            "http://schemas.android.com/apk/res-auto",
            ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
        )

        if (attr == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "MotionLayout must specify a $ATTR_CONSTRAINT_LAYOUT_DESCRIPTION attribute pointing to a valid motion scene file"
            )
            return
        }

        val value = attr.value
        if (!value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION must specify a valid motion scene file"
            )
        }
    }

    companion object {
        private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"

        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "MotionLayout scene file reference",
            explanation = "A motion scene file specifies the animations used in a MotionLayout. " +
                "The $ATTR_CONSTRAINT_LAYOUT_DESCRIPTION is required to specify a valid motion scene file.",
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