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

    /**
     * Pending incidents: pairs of (location, message) collected during visitElement that need to be
     * reported after all files have been checked (so we can verify scene file existence).
     */
    private data class PendingIncident(
        val context: XmlContext,
        val element: Element,
        val location: Location,
        val sceneReference: String?,
    )

    private val pendingIncidents = mutableListOf<PendingIncident>()

    /** Set of XML file names (without extension) found in the `xml` resource folder. */
    private val xmlResourceFiles = mutableSetOf<String>()

    companion object {
        private val IMPLEMENTATION = Implementation(
            MotionLayoutDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION must specify a scene file",
            explanation =
                "A motion scene file specifies the animations used in a `MotionLayout`. " +
                    "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` is required to specify a valid " +
                    "motion scene file.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.XML

    override fun getApplicableElements(): Collection<String> = listOf(MOTION_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        // We only care about MotionLayout elements in layout files.
        if (context.resourceFolderType != ResourceFolderType.LAYOUT) {
            // Track xml resource file names so we can validate references later.
            return
        }

        val descriptionAttr = element.getAttributeNodeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

        if (descriptionAttr == null) {
            // Attribute is missing entirely.
            pendingIncidents.add(
                PendingIncident(
                    context = context,
                    element = element,
                    location = context.getLocation(element),
                    sceneReference = null,
                )
            )
            return
        }

        val value = descriptionAttr.value?.trim()
        if (value.isNullOrEmpty()) {
            pendingIncidents.add(
                PendingIncident(
                    context = context,
                    element = element,
                    location = context.getLocation(descriptionAttr),
                    sceneReference = null,
                )
            )
            return
        }

        // The value should be of the form @xml/scene_file_name
        pendingIncidents.add(
            PendingIncident(
                context = context,
                element = element,
                location = context.getLocation(descriptionAttr),
                sceneReference = value,
            )
        )
    }

    override fun afterCheckRootProject(context: Context) {
        // Collect all xml resource file names from the project's resources.
        val client = context.client
        val project = context.project
        val resources = client.getResources(project, true)

        for (incident in pendingIncidents) {
            val ref = incident.sceneReference

            if (ref == null) {
                // Missing attribute or empty value.
                incident.context.report(
                    ISSUE,
                    incident.element,
                    incident.location,
                    "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must specify a valid " +
                        "motion scene file reference (e.g. `@xml/motion_scene`)",
                )
                continue
            }

            // Validate the reference format: must start with @xml/
            if (!ref.startsWith("@xml/")) {
                incident.context.report(
                    ISSUE,
                    incident.element,
                    incident.location,
                    "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must point to an XML " +
                        "resource file in the `xml` resource folder (e.g. `@xml/motion_scene`), " +
                        "but was `$ref`",
                )
                continue
            }

            // Extract the file name from the reference.
            val sceneName = ref.removePrefix("@xml/")
            if (sceneName.isBlank()) {
                incident.context.report(
                    ISSUE,
                    incident.element,
                    incident.location,
                    "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must specify a valid " +
                        "motion scene file reference (e.g. `@xml/motion_scene`)",
                )
                continue
            }

            // Check whether the referenced xml resource actually exists.
            val xmlItems = resources.getResources(
                com.android.ide.common.rendering.api.ResourceNamespace.TODO(),
                com.android.resources.ResourceType.XML,
                sceneName,
            )

            if (xmlItems.isEmpty()) {
                incident.context.report(
                    ISSUE,
                    incident.element,
                    incident.location,
                    "The motion scene file `@xml/$sceneName` could not be found",
                )
            }
        }

        pendingIncidents.clear()
    }
}