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
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val APP_NS = "http://schemas.android.com/apk/res-auto"
        private const val SCENE_PREFIX = "@xml/"

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

        // Data class to hold pending verifications
        private data class PendingCheck(
            val context: XmlContext,
            val element: Element,
            val location: Location,
            val sceneFileRef: String?,
        )
    }

    // Map from project to list of pending checks (scene file references to validate)
    private val pendingChecks = mutableListOf<PendingCheck>()

    // Set of known xml resource names found during scanning
    private val xmlResourceNames = mutableSetOf<String>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(MOTION_LAYOUT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.resourceFolderType != ResourceFolderType.LAYOUT) {
            return
        }

        // Look for the layoutDescription attribute in the app namespace
        var descriptionAttr = element.getAttributeNS(APP_NS, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
        if (descriptionAttr.isNullOrEmpty()) {
            descriptionAttr = element.getAttributeNS(ANDROID_NS, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
        }

        val location = context.getElementLocation(element)

        if (descriptionAttr.isNullOrEmpty()) {
            // No layoutDescription attribute at all
            pendingChecks.add(
                PendingCheck(
                    context = context,
                    element = element,
                    location = location,
                    sceneFileRef = null,
                )
            )
        } else {
            // Has attribute, record for later validation
            pendingChecks.add(
                PendingCheck(
                    context = context,
                    element = element,
                    location = location,
                    sceneFileRef = descriptionAttr,
                )
            )
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // Collect all xml resource names from the project
        val client = context.client
        val project = context.project
        val resources = client.getResources(project, true)

        // Check each pending MotionLayout element
        for (check in pendingChecks) {
            val sceneRef = check.sceneFileRef

            if (sceneRef == null) {
                // Missing layoutDescription attribute entirely
                check.context.report(
                    issue = ISSUE,
                    location = check.location,
                    message = "Missing `app:layoutDescription` attribute that specifies a `MotionScene` file",
                )
                continue
            }

            // Validate that the reference points to an @xml/ resource
            if (!sceneRef.startsWith(SCENE_PREFIX) && !sceneRef.startsWith("@xml/")) {
                check.context.report(
                    issue = ISSUE,
                    location = check.location,
                    message = "The `layoutDescription` attribute must point to a MotionScene file (`@xml/...`), got `$sceneRef`",
                )
                continue
            }

            // Extract the resource name from the reference (e.g. "@xml/my_scene" -> "my_scene")
            val resourceName = sceneRef.substringAfter("@xml/").substringAfter("/")
            if (resourceName.isEmpty()) {
                check.context.report(
                    issue = ISSUE,
                    location = check.location,
                    message = "The `layoutDescription` attribute must reference a valid MotionScene file",
                )
                continue
            }

            // Try to resolve the xml resource
            val xmlItems = resources.getResources(
                com.android.ide.common.rendering.api.ResourceNamespace.TODO(),
                com.android.resources.ResourceType.XML,
                resourceName,
            )

            if (xmlItems.isEmpty()) {
                check.context.report(
                    issue = ISSUE,
                    location = check.location,
                    message = "Could not find MotionScene file `$sceneRef`",
                )
            }
        }

        pendingChecks.clear()
    }
}