package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class MotionLayoutDetector : Detector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        val isMotionLayout = tagName == "MotionLayout" || tagName.endsWith(".MotionLayout")

        var attribute: Attr? = null
        val attributes = element.attributes
        if (attributes != null) {
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as Attr
                val localName = attr.localName ?: attr.nodeName.substringAfter(':')
                if (localName == "layoutDescription") {
                    attribute = attr
                    break
                }
            }
        }

        if (isMotionLayout && attribute == null) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                element,
                context.getNameLocation(element),
                "The attribute `app:layoutDescription` is missing"
            )
        } else if (attribute != null) {
            val value = attribute.value
            if (!value.startsWith("@xml/")) {
                context.report(
                    INVALID_SCENE_FILE_REFERENCE,
                    attribute,
                    context.getValueLocation(attribute),
                    "The `layoutDescription` attribute must specify a scene file"
                )
            } else {
                val sceneFileName = value.substringAfter("@xml/")
                var fileExists = false
                
                val resourceFolders = context.project.resourceFolders
                for (folder in resourceFolders) {
                    val xmlFolder = java.io.File(folder, "xml")
                    if (xmlFolder.isDirectory) {
                        val sceneFile = java.io.File(xmlFolder, "$sceneFileName.xml")
                        if (sceneFile.exists()) {
                            fileExists = true
                            break
                        }
                    }
                }
                
                if (!fileExists) {
                    val currentFile = context.file
                    val resFolder = currentFile.parentFile?.parentFile
                    if (resFolder != null && resFolder.isDirectory) {
                        val xmlFolder = java.io.File(resFolder, "xml")
                        if (xmlFolder.isDirectory) {
                            val sceneFile = java.io.File(xmlFolder, "$sceneFileName.xml")
                            if (sceneFile.exists()) {
                                fileExists = true
                            }
                        }
                    }
                }

                if (!fileExists) {
                    context.report(
                        INVALID_SCENE_FILE_REFERENCE,
                        attribute,
                        context.getValueLocation(attribute),
                        "The associated scene file `@xml/$sceneFileName` does not exist"
                    )
                }
            }
        }

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                checkElement(context, child)
            }
        }
    }

    companion object {
        @JvmField
        val INVALID_SCENE_FILE_REFERENCE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "MotionLayout missing or invalid scene file reference",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                The `app:layoutDescription` is required to specify a valid motion scene file.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}