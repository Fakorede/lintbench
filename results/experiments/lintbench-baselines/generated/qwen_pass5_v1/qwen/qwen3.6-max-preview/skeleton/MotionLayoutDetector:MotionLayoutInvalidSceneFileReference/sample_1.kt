package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class MotionLayoutDetector : ResourceXmlDetector() {

    companion object {
        private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"

        private val IMPLEMENTATION = Implementation(
            MotionLayoutDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION must specify a scene file",
            explanation = "A motion scene file specifies the animations used in a MotionLayout. The $ATTR_CONSTRAINT_LAYOUT_DESCRIPTION is required to specify a valid motion scene file.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("androidx.constraintlayout.motion.widget.MotionLayout")
    }

    override fun afterCheckRootProject(context: Context) {
        // No-op
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = (0 until element.attributes.length)
            .map { element.attributes.item(it) }
            .find { it.localName == ATTR_CONSTRAINT_LAYOUT_DESCRIPTION }

        if (attr == null) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Missing $ATTR_CONSTRAINT_LAYOUT_DESCRIPTION attribute"
            )
            return
        }

        val value = attr.nodeValue
        if (value == null || !value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                context.getLocation(attr),
                "$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION must reference a motion scene file in @xml/"
            )
        }
    }
}