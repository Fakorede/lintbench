package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class MotionLayoutDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val INVALID_SCENE_FILE_REFERENCE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "`${SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION}` must specify a scene file",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                The `${SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION}` is required to specify a valid motion scene file.
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val MOTION_LAYOUT_CLASS = "MotionLayout"
        private const val MOTION_LAYOUT_FULL_CLASS = "androidx.constraintlayout.motion.widget.MotionLayout"
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(MOTION_LAYOUT_CLASS, MOTION_LAYOUT_FULL_CLASS)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Try to find the layoutDescription attribute with AUTO_URI namespace
        val attrNode = element.getAttributeNodeNS(
            SdkConstants.AUTO_URI,
            SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
        )

        val descriptionValue = attrNode?.value?.takeIf { it.isNotEmpty() }

        if (descriptionValue == null) {
            // Attribute is missing or empty - report error
            val location = if (attrNode != null) {
                context.getLocation(attrNode)
            } else {
                context.getLocation(element)
            }
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                element,
                location,
                "`${SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION}` must specify a scene file"
            )
            return
        }

        // Check that the value looks like a valid resource reference (e.g. @xml/...)
        if (!descriptionValue.startsWith("@xml/")) {
            val location = context.getLocation(attrNode)
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                element,
                location,
                "`${SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION}` must specify a scene file"
            )
        }
    }
}