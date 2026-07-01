package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class MotionLayoutDetector : ResourceXmlDetector(), XmlScanner {

    private val scenes = mutableSetOf<String>()
    private val usages = mutableListOf<Usage>()

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT ||
               folderType == com.android.resources.ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            "MotionLayout",
            "androidx.constraintlayout.motion.widget.MotionLayout",
            "com.android.constraintlayout.motion.widget.MotionLayout",
            "MotionScene"
        )
    }

    override fun beforeCheckRootProject(context: Context) {
        scenes.clear()
        usages.clear()
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val tagName = element.tagName
        if (tagName == "MotionScene") {
            val fileName = context.file.nameWithoutExtension
            scenes.add(fileName)
            return
        }

        if (tagName == "MotionLayout" ||
            tagName == "androidx.constraintlayout.motion.widget.MotionLayout" ||
            tagName == "com.android.constraintlayout.motion.widget.MotionLayout"
        ) {
            val attribute = element.getAttributeNodeNS(com.android.SdkConstants.AUTO_URI, "layoutDescription")
            if (attribute == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "MotionLayout requires a scene file to be specified using `app:layoutDescription`"
                )
                return
            }

            val value = attribute.value
            if (!value.startsWith("@xml/")) {
                context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Invalid scene file reference; must be a reference to an XML resource (e.g. `@xml/scene`)"
                )
                return
            }

            val sceneName = value.substringAfter("@xml/")
            if (sceneName.isEmpty()) {
                context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Invalid scene file reference"
                )
                return
            }

            usages.add(Usage(context.getValueLocation(attribute), sceneName, value))
        }
    }

    override fun afterCheckRootProject(context: Context) {
        for (usage in usages) {
            if (!scenes.contains(usage.sceneName)) {
                context.report(
                    ISSUE,
                    usage.location,
                    "The scene file `${usage.attributeValue}` does not exist"
                )
            }
        }
    }

    private class Usage(
        val location: Location,
        val sceneName: String,
        val attributeValue: String
    )

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "MotionLayout missing or invalid scene file reference",
            explanation = "A motion scene file specifies the animations used in a `MotionLayout`. The `app:layoutDescription` attribute is required to specify a valid motion scene file.",
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