package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import java.io.File

class MotionLayoutDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf(MOTION_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        val description = element.getAttributeNS(KEY_URL, ATTR_LAYOUT_DESCRIPTION)
        if (description.isBlank()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "$ATTR_LAYOUT_DESCRIPTION must specify a scene file"
            )
            return
        }

        if (!description.startsWith(MOTION_SCENE_RESOURCE_PREFIX)) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "$ATTR_LAYOUT_DESCRIPTION must specify a scene file"
            )
            return
        }

        val fileName = description.substringAfter(MOTION_SCENE_RESOURCE_PREFIX)
        val sceneFileExists = context.project.resourceFolders.any { resDir ->
            File(resDir, "xml/$fileName.xml").exists()
        }

        if (!sceneFileExists) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "The scene file `$fileName` referenced by $ATTR_LAYOUT_DESCRIPTION was not found"
            )
        }
    }

    companion object {
        const val MOTION_LAYOUT = "androidx.constraintlayout.motion.widget.MotionLayout"
        const val ATTR_LAYOUT_DESCRIPTION = "layoutDescription"
        const val MOTION_SCENE_RESOURCE_PREFIX = "@xml/"
        const val KEY_URL = "http://schemas.android.com/apk/res-auto"

        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "Invalid MotionLayout scene file reference",
            explanation = """
                A `MotionLayout` requires the `$ATTR_LAYOUT_DESCRIPTION` attribute to reference a valid motion scene file in `res/xml/`.
            """.trimIndent(),
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