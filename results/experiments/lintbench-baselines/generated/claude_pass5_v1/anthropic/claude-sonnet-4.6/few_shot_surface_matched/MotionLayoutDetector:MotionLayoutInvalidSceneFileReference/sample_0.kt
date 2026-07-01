package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TAG_LAYOUT
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class MotionLayoutDetector : ResourceXmlDetector(), XmlScanner {

    private val pendingElements = mutableListOf<Pair<XmlContext, Element>>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(MOTION_LAYOUT_CLASS, MOTION_LAYOUT_CLASS_SIMPLE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        pendingElements.add(Pair(context, element))
    }

    override fun afterCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        for ((xmlContext, element) in pendingElements) {
            checkElement(xmlContext, element)
        }
        pendingElements.clear()
    }

    private fun checkElement(context: XmlContext, element: Element) {
        // Check for the layoutDescription attribute in the app (AUTO_URI) namespace
        val descriptionAttr = element.getAttributeNodeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

        if (descriptionAttr == null || descriptionAttr.value.isNullOrBlank()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must specify a valid motion scene file"
            )
            return
        }

        val value = descriptionAttr.value.trim()

        // The value should be a reference like @xml/scene_file
        if (!value.startsWith("@xml/") && !value.startsWith("@+xml/")) {
            context.report(
                ISSUE,
                element,
                context.getValueLocation(descriptionAttr),
                "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must specify a valid motion scene file"
            )
            return
        }

        // Extract the resource name and verify the file exists in the project
        val resourceName = if (value.startsWith("@+xml/")) {
            value.substring("@+xml/".length)
        } else {
            value.substring("@xml/".length)
        }

        if (resourceName.isBlank()) {
            context.report(
                ISSUE,
                element,
                context.getValueLocation(descriptionAttr),
                "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must specify a valid motion scene file"
            )
            return
        }

        // Check whether the referenced XML resource actually exists
        val client = context.client
        val project = context.project
        val resources = client.getResources(project, com.android.tools.lint.detector.api.ResourceRepositoryScope.ALL_DEPENDENCIES)
        val items = resources.getResources(
            com.android.ide.common.rendering.api.ResourceNamespace.TODO(),
            com.android.resources.ResourceType.XML,
            resourceName
        )

        if (items.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getValueLocation(descriptionAttr),
                "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must specify a valid motion scene file"
            )
        }
    }

    companion object {
        private const val MOTION_LAYOUT_CLASS =
            "androidx.constraintlayout.motion.widget.MotionLayout"
        private const val MOTION_LAYOUT_CLASS_SIMPLE = "MotionLayout"
        const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "Invalid motion scene file reference",
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
}