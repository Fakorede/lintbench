package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceFolderType
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class MotionLayoutDetector : ResourceXmlDetector(), XmlScanner {

  private val sceneReferences = mutableListOf<SceneReference>()

  private data class SceneReference(val sceneName: String, val location: Location)

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.LAYOUT
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf(MOTION_LAYOUT_TAG)
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val attribute = element.getAttributeNodeNS(AUTO_URI, ATTR_LAYOUT_DESCRIPTION) ?: return
    val value = attribute.value ?: return
    val location = context.getValueLocation(attribute)

    if (!value.startsWith("@xml/")) {
      sceneReferences.add(SceneReference("", location))
      return
    }

    val sceneName = value.substring(5)
    if (sceneName.isBlank() || sceneName.contains('/')) {
      sceneReferences.add(SceneReference("", location))
      return
    }

    sceneReferences.add(SceneReference(sceneName, location))
  }

  override fun afterCheckRootProject(context: Context) {
    if (sceneReferences.isEmpty()) {
      return
    }

    val existingScenes = mutableSetOf<String>()
    for (resDir in context.project.resourceFolders) {
      val xmlDirs = resDir.listFiles { file: java.io.File ->
        file.isDirectory && file.name.startsWith("xml")
      } ?: continue

      for (xmlDir in xmlDirs) {
        xmlDir.listFiles { file: java.io.File ->
          file.isFile && file.name.endsWith(".xml")
        }?.forEach { existingScenes.add(it.nameWithoutExtension) }
      }
    }

    for (reference in sceneReferences) {
      if (reference.sceneName.isEmpty() || reference.sceneName !in existingScenes) {
        context.report(
          ISSUE,
          reference.location,
          "The `layoutDescription` attribute must specify a valid motion scene file (e.g. `@xml/scene`)."
        )
      }
    }

    sceneReferences.clear()
  }

  companion object {
    private const val MOTION_LAYOUT_TAG = "androidx.constraintlayout.motion.widget.MotionLayout"
    private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
    private const val ATTR_LAYOUT_DESCRIPTION = "layoutDescription"

    @JvmField
    val ISSUE = Issue.create(
      id = "MotionLayoutInvalidSceneFileReference",
      briefDescription = "Invalid MotionLayout scene file reference",
      explanation = "A motion scene file specifies the animations used in a `MotionLayout`. " +
        "The `layoutDescription` attribute is required to specify a valid motion scene file.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(MotionLayoutDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}