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
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class MotionLayoutDetector : ResourceXmlDetector() {

    /**
     * List of (context, element, location) tuples collected during visitElement.
     * We validate them in afterCheckRootProject so we can check whether the
     * referenced scene file actually exists in the project's xml/ resource folder.
     */
    private data class PendingCheck(
        val context: XmlContext,
        val element: Element,
        val location: Location,
        val reference: String?,
    )

    private val pendingChecks = mutableListOf<PendingCheck>()

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
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableElements(): Collection<String> = listOf(MOTION_LAYOUT)

    override fun afterCheckRootProject(context: Context) {
        for (check in pendingChecks) {
            val reference = check.reference

            // If the attribute is missing or blank, report immediately.
            if (reference.isNullOrBlank()) {
                check.context.report(
                    ISSUE,
                    check.element,
                    check.location,
                    "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must specify a valid" +
                        " motion scene file",
                )
                continue
            }

            // The reference should be of the form @xml/<name>.
            // Validate that it starts with @xml/
            if (!reference.startsWith("@xml/")) {
                check.context.report(
                    ISSUE,
                    check.element,
                    check.location,
                    "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must be an xml" +
                        " resource reference (`@xml/<filename>`), but was `$reference`",
                )
                continue
            }

            // Extract the file name and check that the resource exists.
            val fileName = reference.removePrefix("@xml/")
            if (fileName.isBlank()) {
                check.context.report(
                    ISSUE,
                    check.element,
                    check.location,
                    "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must specify a valid" +
                        " motion scene file",
                )
                continue
            }

            // Look for the file in the xml resource directories.
            val client = context.client
            val resourceDirs = context.project.resourceFolders
            var found = false
            outer@ for (resDir in resourceDirs) {
                val xmlDir = resDir.listFiles() ?: continue
                for (dir in xmlDir) {
                    if (!dir.isDirectory) continue
                    val dirName = dir.name
                    // Accept xml, xml-*, etc.
                    if (dirName != "xml" && !dirName.startsWith("xml-")) continue
                    val files = dir.listFiles() ?: continue
                    for (file in files) {
                        val nameWithoutExt = file.nameWithoutExtension
                        if (nameWithoutExt == fileName) {
                            found = true
                            break@outer
                        }
                    }
                }
            }

            if (!found) {
                check.context.report(
                    ISSUE,
                    check.element,
                    check.location,
                    "The motion scene file `$reference` could not be found",
                )
            }
        }

        pendingChecks.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Retrieve the layoutDescription attribute (app:layoutDescription)
        val attr = element.getAttributeNodeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

        val location: Location
        val reference: String?

        if (attr == null) {
            // Attribute is entirely absent.
            location = context.getNameLocation(element)
            reference = null
        } else {
            location = context.getValueLocation(attr)
            reference = attr.value.trim().takeIf { it.isNotEmpty() }
        }

        pendingChecks.add(PendingCheck(context, element, location, reference))
    }
}