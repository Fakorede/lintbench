package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class MotionLayoutDetector : ResourceXmlDetector() {

    companion object {
        private const val MOTION_LAYOUT = "androidx.constraintlayout.motion.widget.MotionLayout"
        private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"

        private val IMPLEMENTATION = Implementation(
            MotionLayoutDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION must specify a scene file",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` is required to specify a valid motion \
                scene file.
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    // Map from scene file reference value to the location where it was referenced,
    // collected during visitElement, checked in afterCheckRootProject
    private val sceneFileReferences = mutableListOf<Pair<String, Location>>()

    // Set of known xml resource names (without @xml/ prefix)
    private val xmlResources = mutableSetOf<String>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(MOTION_LAYOUT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Track xml resources we encounter
        if (context.resourceFolderType == ResourceFolderType.XML) {
            // The root element of an xml file means this file exists as an xml resource
            val fileName = context.file.nameWithoutExtension
            xmlResources.add(fileName)
            return
        }

        // We're in a layout file visiting a MotionLayout element
        val descriptionAttr = element.getAttributeNodeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
            ?: element.getAttributeNode(ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

        if (descriptionAttr == null || descriptionAttr.value.isBlank()) {
            // Missing the attribute entirely
            val location = context.getElementLocation(element)
            context.report(
                ISSUE,
                element,
                location,
                "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute is missing",
            )
            return
        }

        val value = descriptionAttr.value
        // The value should be of the form @xml/scene_file
        if (!value.startsWith("@xml/")) {
            val location = context.getValueLocation(descriptionAttr)
            context.report(
                ISSUE,
                element,
                location,
                "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must be a XML resource reference (`@xml/...`)",
            )
            return
        }

        // Extract the resource name and record it for later validation
        val resourceName = value.removePrefix("@xml/")
        if (resourceName.isBlank()) {
            val location = context.getValueLocation(descriptionAttr)
            context.report(
                ISSUE,
                element,
                location,
                "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must specify a valid motion scene file",
            )
            return
        }

        val location = context.getValueLocation(descriptionAttr)
        sceneFileReferences.add(Pair(resourceName, location))
    }

    override fun afterCheckRootProject(context: Context) {
        // Validate that each referenced scene file actually exists as an xml resource
        for ((resourceName, location) in sceneFileReferences) {
            if (!xmlResources.contains(resourceName)) {
                context.report(
                    ISSUE,
                    location,
                    "The motion scene file `@xml/$resourceName` could not be found",
                )
            }
        }
        // Clear state for next run
        sceneFileReferences.clear()
        xmlResources.clear()
    }
}