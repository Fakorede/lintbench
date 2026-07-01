package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

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
            // Also handle short form
            "MotionLayout"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Check if this element is actually a MotionLayout
        val tagName = element.tagName
        if (tagName != MOTION_LAYOUT_CLASS && tagName != "MotionLayout") {
            return
        }

        // Look for the layoutDescription attribute in the motion namespace or app namespace
        val appNs = SdkConstants.AUTO_URI

        val descriptionAttr = element.getAttributeNodeNS(appNs, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
            ?: run {
                // Attribute is missing entirely
                context.report(
                    INVALID_SCENE_FILE_REFERENCE,
                    element,
                    context.getLocation(element),
                    "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
                )
                return
            }

        val value = descriptionAttr.value
        if (value.isNullOrBlank()) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                descriptionAttr,
                context.getLocation(descriptionAttr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
            )
            return
        }

        // The value should be a reference like @xml/scene_file
        if (!value.startsWith("@")) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                descriptionAttr,
                context.getLocation(descriptionAttr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
            )
            return
        }

        // Parse the resource reference: @xml/filename or @layout/filename etc.
        val withoutAt = value.substring(1)
        // Handle @+xml/... or @xml/...
        val normalized = if (withoutAt.startsWith("+")) withoutAt.substring(1) else withoutAt

        val slashIndex = normalized.indexOf('/')
        if (slashIndex < 0) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                descriptionAttr,
                context.getLocation(descriptionAttr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
            )
            return
        }

        val resourceType = normalized.substring(0, slashIndex)
        val resourceName = normalized.substring(slashIndex + 1)

        if (resourceType != MOTION_SCENE_FOLDER) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                descriptionAttr,
                context.getLocation(descriptionAttr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
            )
            return
        }

        if (resourceName.isBlank()) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                descriptionAttr,
                context.getLocation(descriptionAttr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
            )
            return
        }

        // Verify the referenced xml file actually exists in the project
        val client = context.client
        val project = context.project
        val resources = client.getResources(project, ResourceRepositoryScope.ALL_DEPENDENCIES)
        val items = resources.getResources(
            com.android.ide.common.rendering.api.ResourceNamespace.TODO(),
            com.android.resources.ResourceType.XML,
            resourceName
        )

        if (items.isEmpty()) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                descriptionAttr,
                context.getLocation(descriptionAttr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
            )
        }
    }
}