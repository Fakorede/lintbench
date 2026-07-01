package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class MotionLayoutDetector : ResourceXmlDetector() {

    companion object {
        private const val MOTION_LAYOUT = "MotionLayout"
        private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"
        private const val MOTION_SCENE_FOLDER = "xml"

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
            priority = 8,
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

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            SdkConstants.MOTION_LAYOUT.newName(),
            SdkConstants.MOTION_LAYOUT.oldName(),
            "androidx.constraintlayout.motion.widget.MotionLayout",
            "android.support.constraint.motion.MotionLayout"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val descriptionAttr = element.getAttributeNodeNS(
            SdkConstants.AUTO_URI,
            ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
        ) ?: element.getAttributeNode(ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

        if (descriptionAttr == null || descriptionAttr.value.isNullOrBlank()) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                element,
                context.getNameLocation(element),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
            )
            return
        }

        val value = descriptionAttr.value
        // Value should be in the form @xml/filename
        if (!value.startsWith("@xml/")) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                element,
                context.getValueLocation(descriptionAttr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
            )
            return
        }

        val resourceName = value.removePrefix("@xml/")
        if (resourceName.isBlank()) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                element,
                context.getValueLocation(descriptionAttr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
            )
            return
        }

        // Check that the referenced xml resource file actually exists
        val client = context.client
        val project = context.project
        val resources = client.getResources(project, ResourceRepositoryScope.LOCAL_DEPENDENCIES)
        val items = resources.getResources(
            com.android.ide.common.rendering.api.ResourceNamespace.TODO(),
            com.android.resources.ResourceType.XML,
            resourceName
        )

        if (items.isEmpty()) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                element,
                context.getValueLocation(descriptionAttr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
            )
        }
    }
}