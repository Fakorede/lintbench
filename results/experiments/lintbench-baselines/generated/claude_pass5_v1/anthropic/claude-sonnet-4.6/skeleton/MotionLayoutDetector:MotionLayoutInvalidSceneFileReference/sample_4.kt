package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.MOTION_LAYOUT
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

        // Data class to hold pending validation info
        private data class PendingCheck(
            val context: XmlContext,
            val element: Element,
            val location: Location,
            val sceneFileReference: String?,
        )
    }

    // List of pending checks to validate after all files are processed
    private val pendingChecks = mutableListOf<PendingCheck>()

    // Set of known XML resource files (scene files) found in res/xml
    private val xmlResourceFiles = mutableSetOf<String>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(MOTION_LAYOUT, "androidx.constraintlayout.motion.widget.MotionLayout")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // If this is an XML file (scene file), record it
        if (context.resourceFolderType == ResourceFolderType.XML) {
            // Record the file name without extension as a known scene file
            val fileName = context.file.nameWithoutExtension
            xmlResourceFiles.add(fileName)
            return
        }

        // For layout files, check the MotionLayout element
        val descriptionAttr = element.getAttributeNodeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
            ?: element.getAttributeNode(ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

        if (descriptionAttr == null || descriptionAttr.value.isNullOrBlank()) {
            // Missing the attribute entirely
            val location = context.getLocation(element)
            pendingChecks.add(
                PendingCheck(
                    context = context,
                    element = element,
                    location = location,
                    sceneFileReference = null,
                )
            )
        } else {
            val value = descriptionAttr.value.trim()
            // Expected format: @xml/scene_file_name
            val location = context.getLocation(descriptionAttr)
            pendingChecks.add(
                PendingCheck(
                    context = context,
                    element = element,
                    location = location,
                    sceneFileReference = value,
                )
            )
        }
    }

    override fun afterCheckRootProject(context: Context) {
        for (check in pendingChecks) {
            val reference = check.sceneFileReference

            if (reference == null) {
                // No attribute present
                check.context.report(
                    issue = ISSUE,
                    location = check.location,
                    message = "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute is missing",
                )
                continue
            }

            // Validate the reference format: must be @xml/<name>
            if (!reference.startsWith("@xml/")) {
                check.context.report(
                    issue = ISSUE,
                    location = check.location,
                    message = "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must be a reference to an XML scene file (e.g. `@xml/scene`)",
                )
                continue
            }

            val referencedFileName = reference.removePrefix("@xml/")

            if (referencedFileName.isBlank()) {
                check.context.report(
                    issue = ISSUE,
                    location = check.location,
                    message = "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must reference a valid motion scene file",
                )
                continue
            }

            // Check if the referenced file exists in known XML resources
            if (!xmlResourceFiles.contains(referencedFileName)) {
                check.context.report(
                    issue = ISSUE,
                    location = check.location,
                    message = "The motion scene file `@xml/$referencedFileName` could not be found",
                )
            }
        }

        pendingChecks.clear()
    }
}