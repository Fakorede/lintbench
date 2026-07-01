package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class MotionLayoutDetector : ResourceXmlDetector(), XmlScanner {

  private val sceneReferences = mutableListOf<SceneReference>()

  override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
    return folderType == com.android.resources.ResourceFolderType.LAYOUT
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_MOTION_LAYOUT)
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val attr = element.getAttributeNodeNS(APP_NAMESPACE, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
    if (attr == null) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "A MotionLayout must specify a scene file via the layoutDescription attribute"
      )
      return
    }

    val value = attr.value
    if (value.isNullOrBlank()) {
      context.report(
        ISSUE,
        attr,
        context.getValueLocation(attr),
        "The layoutDescription attribute must specify a scene file"
      )
      return
    }

    if (!value.startsWith("@xml/")) {
      context.report(
        ISSUE,
        attr,
        context.getValueLocation(attr),
        "The layoutDescription attribute must reference a motion scene file in res/xml (e.g. @xml/scene)"
      )
      return
    }

    val name = value.substring("@xml/".length)
    if (name.isBlank()) {
      context.report(
        ISSUE,
        attr,
        context.getValueLocation(attr),
        "The layoutDescription attribute must specify a valid motion scene file name"
      )
      return
    }

    sceneReferences.add(SceneReference(context.getValueLocation(attr), name))
  }

  override fun afterCheckRootProject(context: Context) {
    val repository = context.project.getResourceRepository() ?: return
    for (reference in sceneReferences) {
      val resources = repository.getResources(
        com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO,
        com.android.resources.ResourceType.XML,
        reference.name
      )
      if (resources.isEmpty()) {
        context.report(
          ISSUE,
          reference.location,
          "The motion scene file @xml/${reference.name} does not exist"
        )
      }
    }
    sceneReferences.clear()
  }

  private data class SceneReference(val location: Location, val name: String)

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "MotionLayoutInvalidSceneFileReference",
      briefDescription = "Invalid Motion Scene File Reference",
      explanation = "A motion scene file specifies the animations used in a MotionLayout. " +
        "The layoutDescription attribute is required and must reference a valid motion scene file in res/xml.",
      category = Category.CORRECTNESS,
      priority = 8,
      severity = Severity.ERROR,
      implementation = Implementation(MotionLayoutDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )

    private const val TAG_MOTION_LAYOUT = "androidx.constraintlayout.motion.widget.MotionLayout"
    private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"
    private const val APP_NAMESPACE = "http://schemas.android.com/apk/res-auto"
  }
}