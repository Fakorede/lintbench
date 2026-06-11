package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
import com.android.SdkConstants.TAG_MOTION_LAYOUT
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class MotionLayoutDetector : Detector(), XmlScanner {

    companion object Issues {
        val MISSING_SCENE_FILE: Issue = Issue.create(
            id = "MissingSceneFile",
            briefDescription = "MotionLayout must specify a scene file",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute is required to specify a valid motion scene file.
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
        return listOf(TAG_MOTION_LAYOUT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = element.getAttributeNode(ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
        if (attr == null || attr.value.isEmpty()) {
            context.report(
                MISSING_SCENE_FILE,
                element,
                context.getLocation(element),
                "MotionLayout must specify a scene file"
            )
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }
}