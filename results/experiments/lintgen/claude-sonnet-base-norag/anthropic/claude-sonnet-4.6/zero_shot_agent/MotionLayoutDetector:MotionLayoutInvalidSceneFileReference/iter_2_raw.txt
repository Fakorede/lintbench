package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
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

        private val MOTION_LAYOUT_CLASSES = listOf(
            "androidx.constraintlayout.motion.widget.MotionLayout",
            "android.support.constraint.motion.MotionLayout"
        )

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
        return MOTION_LAYOUT_CLASSES
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val descriptionAttr = element.getAttributeNodeNS(
            SdkConstants.AUTO_URI,
            ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
        ) ?: element.getAttributeNode(ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

        if (descriptionAttr == null) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                element,
                context.getNameLocation(element),
                "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute is missing"
            )
            return
        }

        val value = descriptionAttr.value
        if (value.isNullOrBlank()) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                descriptionAttr,
                context.getValueLocation(descriptionAttr),
                "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute is empty"
            )
            return
        }

        // Value should be a resource reference like @xml/scene_file
        if (!value.startsWith("@")) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                descriptionAttr,
                context.getValueLocation(descriptionAttr),
                "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must be a resource reference to a motion scene file"
            )
            return
        }

        // Parse the resource reference manually
        // Format: @[package:]type/name
        val withoutAt = value.substring(1)
        val slashIndex = withoutAt.indexOf('/')
        if (slashIndex < 0) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                descriptionAttr,
                context.getValueLocation(descriptionAttr),
                "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute does not specify a valid resource reference"
            )
            return
        }

        val typePart = withoutAt.substring(0, slashIndex).let {
            // Handle package prefix like package:type
            val colonIndex = it.indexOf(':')
            if (colonIndex >= 0) it.substring(colonIndex + 1) else it
        }
        val namePart = withoutAt.substring(slashIndex + 1)

        // Must reference an xml resource type
        if (typePart != "xml") {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                descriptionAttr,
                context.getValueLocation(descriptionAttr),
                "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must point to a motion scene file in the `xml` resource folder"
            )
            return
        }

        if (namePart.isBlank()) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                descriptionAttr,
                context.getValueLocation(descriptionAttr),
                "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute does not specify a valid resource name"
            )
            return
        }

        // Check that the referenced xml file actually exists
        val client = context.client
        val resources = client.getResources(context.project, com.android.tools.lint.detector.api.LintClient.Companion.FLAG_NONE.let {
            // Use the project's resource repository
            context.project
        }.let {
            client.getResources(it, false)
        }.let { return@let it }
            .let { return })

        // Fallback: just check via resource repository if available
        try {
            val resClient = context.client
            val project = context.project
            val repo = resClient.getResources(project, false)
            val namespace = com.android.ide.common.rendering.api.ResourceNamespace.TODO()
            val items = repo.getResources(namespace, ResourceType.XML, namePart)
            if (items.isEmpty()) {
                context.report(
                    INVALID_SCENE_FILE_REFERENCE,
                    descriptionAttr,
                    context.getValueLocation(descriptionAttr),
                    "The motion scene file `$namePart` does not exist"
                )
            }
        } catch (e: Exception) {
            // If we can't check, skip the existence check
        }
    }
}