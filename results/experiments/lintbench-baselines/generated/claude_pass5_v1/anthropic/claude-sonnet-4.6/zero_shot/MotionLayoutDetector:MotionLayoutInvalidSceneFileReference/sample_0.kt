package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.xmlpull.v1.XmlPullParser

class MotionLayoutDetector : ResourceXmlDetector() {

    companion object {
        private const val MOTION_LAYOUT_CLASS = "androidx.constraintlayout.motion.widget.MotionLayout"
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

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            MOTION_LAYOUT_CLASS,
            SdkConstants.MOTION_LAYOUT.newName(),
            SdkConstants.MOTION_LAYOUT.oldName()
        )
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val descriptionAttr = element.getAttributeNodeNS(
            SdkConstants.AUTO_URI,
            ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
        ) ?: element.getAttributeNode(ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

        if (descriptionAttr == null) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                element,
                context.getNameLocation(element),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
            )
            return
        }

        val value = descriptionAttr.value
        if (value.isNullOrBlank()) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                descriptionAttr,
                context.getValueLocation(descriptionAttr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
            )
            return
        }

        // Value should be a resource reference like @xml/scene_file
        if (!value.startsWith("@")) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                descriptionAttr,
                context.getValueLocation(descriptionAttr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
            )
            return
        }

        // Validate that the reference points to an xml resource
        val resourceUrl = ResourceUrl.parse(value)
        if (resourceUrl == null) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                descriptionAttr,
                context.getValueLocation(descriptionAttr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
            )
            return
        }

        if (resourceUrl.type != com.android.resources.ResourceType.XML) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                descriptionAttr,
                context.getValueLocation(descriptionAttr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
            )
            return
        }

        // Check that the referenced XML file actually exists in the project
        val client = context.client
        val resources = client.getResources(context.project, LintClient.ResourceRepositoryScope.LOCAL_DEPENDENCIES)
        val items = resources.getResources(
            com.android.ide.common.rendering.api.ResourceNamespace.TODO(),
            com.android.resources.ResourceType.XML,
            resourceUrl.name
        )

        if (items.isEmpty()) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                descriptionAttr,
                context.getValueLocation(descriptionAttr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
            )
        }
    }
}