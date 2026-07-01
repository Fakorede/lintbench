package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
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
        private const val MOTION_LAYOUT_CLASS = "androidx.constraintlayout.motion.widget.MotionLayout"
        private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"
        private const val MOTION_LAYOUT_SIMPLE = "MotionLayout"

        @JvmField
        val INVALID_SCENE_FILE_REFERENCE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` is required to specify a valid motion \
                scene file.
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

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            MOTION_LAYOUT_CLASS,
            MOTION_LAYOUT_SIMPLE,
            SdkConstants.MOTION_LAYOUT.newName(),
            SdkConstants.MOTION_LAYOUT.oldName()
        ).distinct()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        if (!tagName.endsWith("MotionLayout")) {
            return
        }

        val descriptionAttr = element.getAttributeNodeNS(
            SdkConstants.AUTO_URI,
            ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
        ) ?: element.getAttributeNode(ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

        if (descriptionAttr == null) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                element,
                context.getElementLocation(element),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
            )
            return
        }

        val value = descriptionAttr.value
        if (value.isBlank() || !value.startsWith("@xml/")) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                descriptionAttr,
                context.getLocation(descriptionAttr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
            )
        }
    }
}