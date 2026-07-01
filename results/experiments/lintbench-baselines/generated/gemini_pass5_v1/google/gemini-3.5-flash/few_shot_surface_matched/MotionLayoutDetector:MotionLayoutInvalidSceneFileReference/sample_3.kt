package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class MotionLayoutDetector : ResourceXmlDetector(), XmlScanner {

    private val motionLayouts = mutableListOf<MotionLayoutInfo>()

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            "MotionLayout",
            "androidx.constraintlayout.motion.widget.MotionLayout",
            "com.android.constraintlayout.motion.widget.MotionLayout"
        )
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        var attr = element.getAttributeNodeNS("http://schemas.android.com/apk/res-auto", "layoutDescription")
        if (attr == null) {
            attr = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "layoutDescription")
        }
        if (attr == null) {
            attr = element.getAttributeNode("layoutDescription")
        }

        if (attr == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "MotionLayout requires a `layoutDescription` attribute"
            )
            return
        }

        val value = attr.value
        if (value.isNullOrEmpty()) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "The `layoutDescription` attribute cannot be empty"
            )
            return
        }

        if (!value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "The `layoutDescription` must specify a scene file (e.g. `@xml/scene_file`)"
            )
            return
        }

        val sceneName = value.substringAfter("@xml/")
        if (sceneName.isEmpty()) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "The `layoutDescription` must specify a scene file (e.g. `@xml/scene_file`)"
            )
            return
        }

        motionLayouts.add(MotionLayoutInfo(context, attr, sceneName))
    }

    override fun afterCheckRootProject(context: Context) {
        for (info in motionLayouts) {
            if (!xmlResourceExists(info.context, info.sceneName)) {
                info.context.report(
                    ISSUE,
                    info.attribute,
                    info.context.getValueLocation(info.attribute),
                    "The motion scene file `@xml/${info.sceneName}` does not exist"
                )
            }
        }
        motionLayouts.clear()
    }

    private fun xmlResourceExists(context: Context, resourceName: String): Boolean {
        for (resFolder in context.project.resourceFolders) {
            val xmlFolder = java.io.File(resFolder, "xml")
            if (xmlFolder.isDirectory) {
                val file = java.io.File(xmlFolder, "$resourceName.xml")
                if (file.isFile) {
                    return true
                }
            }
        }
        return false
    }

    private class MotionLayoutInfo(
        val context: XmlContext,
        val attribute: org.w3c.dom.Attr,
        val sceneName: String
    )

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "MotionLayout missing or invalid scene file reference",
            explanation = "A motion scene file specifies the animations used in a `MotionLayout`. " +
                    "The `layoutDescription` attribute is required to specify a valid motion scene file.",
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