package com.android.tools.lint.checks

import com.android.SdkConstants.AUTO_URI
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import java.io.File

class MotionLayoutDetector : Detector(), XmlScanner {
    override fun getApplicableElements(): Collection<String> = listOf(MOTION_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = element.getAttributeNodeNS(AUTO_URI, ATTR_LAYOUT_DESCRIPTION)
        if (attr == null || attr.value.isBlank()) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "$ATTR_LAYOUT_DESCRIPTION must specify a scene file"
            )
            return
        }

        val description = attr.value.trim()
        if (!description.startsWith(MOTION_SCENE_PREFIX)) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "$ATTR_LAYOUT_DESCRIPTION must specify a valid motion scene file"
            )
            return
        }

        val fileName = description.substringAfter(MOTION_SCENE_PREFIX)
        val found = context.project?.resourceFolders?.any {
            File(it, "xml/$fileName.xml").exists()
        } ?: false

        if (!found) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "The scene file \"$fileName.xml\" referenced by $ATTR_LAYOUT_DESCRIPTION could not be found"
            )
        }
    }

    companion object {
        const val MOTION_LAYOUT = "androidx.constraintlayout.motion.widget.MotionLayout"
        const val ATTR_LAYOUT_DESCRIPTION = "layoutDescription"
        const val MOTION_SCENE_PREFIX = "@xml/"
        private const val ISSUE_ID = "MotionLayoutInvalidSceneFileReference"

        private val IMPLEMENTATION = Implementation(
            MotionLayoutDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE
        )

        val ISSUE = Issue.create(
            ISSUE_ID,
            "Invalid MotionLayout scene file reference",
            "The $ATTR_LAYOUT_DESCRIPTION attribute must reference a valid motion scene XML resource.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            IMPLEMENTATION
        )
    }
}