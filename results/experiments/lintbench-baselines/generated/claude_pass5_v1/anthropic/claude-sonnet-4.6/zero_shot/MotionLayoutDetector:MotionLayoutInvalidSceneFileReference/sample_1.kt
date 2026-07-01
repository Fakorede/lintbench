package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.xmlpull.v1.XmlPullParser

class MotionLayoutDetector : ResourceXmlDetector() {

    companion object {
        private const val MOTION_LAYOUT = "MotionLayout"
        private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"
        private const val MOTION_LAYOUT_CLASS =
            "androidx.constraintlayout.motion.widget.MotionLayout"
        private const val MOTION_LAYOUT_CLASS_LEGACY =
            "androidx.constraintlayout.widget.MotionLayout"

        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "Invalid motion scene file reference",
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

        private val MOTION_LAYOUT_NAMES = setOf(
            MOTION_LAYOUT,
            MOTION_LAYOUT_CLASS,
            MOTION_LAYOUT_CLASS_LEGACY
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String>? {
        return MOTION_LAYOUT_NAMES
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val tagName = element.tagName
        val localName = element.localName ?: tagName

        // Check if this is a MotionLayout element
        val isMotionLayout = tagName == MOTION_LAYOUT ||
                tagName == MOTION_LAYOUT_CLASS ||
                tagName == MOTION_LAYOUT_CLASS_LEGACY ||
                localName == MOTION_LAYOUT

        if (!isMotionLayout) return

        // Check for the layoutDescription attribute
        val hasLayoutDescription = element.hasAttributeNS(
            SdkConstants.AUTO_URI,
            ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
        ) || element.hasAttribute("app:$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION") ||
                element.hasAttribute(ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

        val layoutDescriptionValue = element.getAttributeNS(
            SdkConstants.AUTO_URI,
            ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
        ).takeIf { it.isNotEmpty() }
            ?: element.getAttribute("app:$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION").takeIf { it.isNotEmpty() }
            ?: element.getAttribute(ATTR_CONSTRAINT_LAYOUT_DESCRIPTION).takeIf { it.isNotEmpty() }

        if (layoutDescriptionValue == null || layoutDescriptionValue.isEmpty()) {
            // Missing the layoutDescription attribute entirely, or it's empty
            val location = context.getElementLocation(element)
            context.report(
                ISSUE,
                element,
                location,
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
            )
            return
        }

        // Validate that the value is a proper resource reference (e.g., @xml/scene_file)
        if (!layoutDescriptionValue.startsWith("@")) {
            val attr = element.getAttributeNodeNS(
                SdkConstants.AUTO_URI,
                ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
            ) ?: element.getAttributeNode("app:$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION")
            ?: element.getAttributeNode(ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

            val location = if (attr != null) {
                context.getValueLocation(attr)
            } else {
                context.getElementLocation(element)
            }

            context.report(
                ISSUE,
                element,
                location,
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
            )
        }
    }
}