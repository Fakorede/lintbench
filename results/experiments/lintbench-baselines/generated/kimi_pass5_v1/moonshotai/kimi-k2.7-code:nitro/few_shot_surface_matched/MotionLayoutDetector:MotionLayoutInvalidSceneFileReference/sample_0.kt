package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceFolderType
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class MotionLayoutDetector : ResourceXmlDetector(), XmlScanner {

  private val pendingSceneReferences = mutableListOf<SceneReference>()

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.LAYOUT
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf(MOTION_LAYOUT_TAG)
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val attr = element.getAttributeNodeNS(AUTO_URI, ATTR_LAYOUT_DESCRIPTION)
    if (attr == null) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "MotionLayout must specify a motion scene file using `app:layoutDescription`"
      )
      return
    }

    val value = attr.value ?: ""
    if (!value.startsWith("@xml/")) {
      context.report(
        ISSUE,
        attr,
        context.getValueLocation(attr),
        "`app:layoutDescription` must reference a motion scene XML resource (`@xml/...`)"
      )
      return
    }

    val sceneName = value.substring("@xml/".length)
    if (sceneName.isEmpty() || sceneName.contains('/')) {
      context.report(
        ISSUE,
        attr,
        context.getValueLocation(attr),
        "`app:layoutDescription` must reference a valid `@xml/...` motion scene resource"
      )
      return
    }

    pendingSceneReferences.add(SceneReference(context, attr, sceneName))
  }

  override fun afterCheckRootProject(context: Context) {
    val xmlFolders = context.project.getResourceFolders(ResourceFolderType.XML)
    for (ref in pendingSceneReferences) {
      val exists = xmlFolders.any { it.resolve("${ref.sceneName}.xml").isFile }
      if (!exists) {
        ref.context.report(
          ISSUE,
          ref.attribute,
          ref.context.getValueLocation(ref.attribute),
          "Motion scene file `@xml/${ref.sceneName}` does not exist"
        )
      }
    }
    pendingSceneReferences.clear()
  }

  private data class SceneReference(
    val context: XmlContext,
    val attribute: org.w3c.dom.Attr,
    val sceneName: String
  )

  companion object {
    private const val MOTION_LAYOUT_TAG = "androidx.constraintlayout.motion.widget.MotionLayout"
    private const val ATTR_LAYOUT_DESCRIPTION = "layoutDescription"
    private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"

    @JvmField
    val ISSUE = Issue.create(
      id = "MotionLayoutInvalidSceneFileReference",
      briefDescription = "MotionLayout scene file reference is invalid",
      explanation = "A motion scene file specifies the animations used in a MotionLayout. " +
        "The `app:layoutDescription` attribute is required and must reference an existing " +
        "XML resource in the `xml/` resource directory, e.g. `@xml/scene`.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(MotionLayoutDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}