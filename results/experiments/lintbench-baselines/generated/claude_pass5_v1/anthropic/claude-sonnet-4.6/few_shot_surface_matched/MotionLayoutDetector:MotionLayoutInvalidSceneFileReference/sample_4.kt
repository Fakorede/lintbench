package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_LAYOUT_RESOURCE_PREFIX
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.FD_RES_XML
import com.android.SdkConstants.RES_FOLDER
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class MotionLayoutDetector : ResourceXmlDetector() {

    private val MOTION_LAYOUT = "androidx.constraintlayout.motion.widget.MotionLayout"
    private val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"

    // Map from XmlContext to the attribute node, stored so we can check after all resources parsed
    private val pendingReferences = mutableListOf<Pair<XmlContext, org.w3c.dom.Attr>>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(MOTION_LAYOUT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attribute = element.getAttributeNodeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
        if (attribute == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must be set on `MotionLayout`"
            )
            return
        }

        val value = attribute.value
        if (value.isBlank()) {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file"
            )
            return
        }

        // Store for later validation once all resources are known
        pendingReferences.add(Pair(context, attribute))
    }

    override fun afterCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        for ((xmlContext, attribute) in pendingReferences) {
            val value = attribute.value
            // Value should be of the form @xml/filename
            val resolvedFile = resolveSceneFile(xmlContext, value)
            if (!resolvedFile) {
                xmlContext.report(
                    ISSUE,
                    attribute,
                    xmlContext.getValueLocation(attribute),
                    "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a valid motion scene file; " +
                        "`$value` could not be found"
                )
            }
        }
        pendingReferences.clear()
    }

    private fun resolveSceneFile(context: XmlContext, value: String): Boolean {
        // Accept @xml/<name> references
        if (!value.startsWith("@xml/")) {
            return false
        }
        val resourceName = value.removePrefix("@xml/")
        if (resourceName.isBlank()) {
            return false
        }

        // Try to find the file in the project's resource directories
        val project = context.project
        val resourceFolders = project.resourceFolders
        for (resFolder in resourceFolders) {
            val xmlFolder = resFolder.resolve(FD_RES_XML)
            if (xmlFolder.exists() && xmlFolder.isDirectory) {
                val sceneFile = xmlFolder.resolve(resourceName + DOT_XML)
                if (sceneFile.exists()) {
                    return true
                }
            }
        }

        // Also check via the lint client's resource repository if available
        val client = context.client
        val resources = client.getResources(project, true)
        val items = resources.getResources(
            com.android.ide.common.rendering.api.ResourceNamespace.TODO(),
            com.android.resources.ResourceType.XML,
            resourceName
        )
        if (items.isNotEmpty()) {
            return true
        }

        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "Invalid motion scene file reference",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                The `layoutDescription` attribute is required to specify a valid motion scene file.
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
}