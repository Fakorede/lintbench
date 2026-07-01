package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class MotionLayoutDetector : ResourceXmlDetector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("androidx.constraintlayout.motion.widget.MotionLayout")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = element.getAttributeNodeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
        if (attr == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing $ATTR_CONSTRAINT_LAYOUT_DESCRIPTION attribute. A motion scene file must be specified."
            )
            return
        }

        val value = attr.value
        if (!value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "The $ATTR_CONSTRAINT_LAYOUT_DESCRIPTION attribute must specify a valid motion scene file (e.g., @xml/scene_file)."
            )
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // No project-wide aggregation required for this check
    }

    companion object {
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"

        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "Invalid scene file reference in MotionLayout",
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