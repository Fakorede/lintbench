package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TAG_MOTION_LAYOUT
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element
import java.io.File

class MotionLayoutDetector : ResourceXmlDetector(), XmlScanner {

  private val references = mutableListOf<SceneReference>()

  override fun appliesTo(folderType: ResourceFolderType, fileName: String): Boolean {
    return folderType == ResourceFolderType.LAYOUT
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_MOTION_LAYOUT)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val attr = element.getAttributeNodeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
    if (attr == null) {
      context.report(
        ISSUE,
        element,
        context.getNameLocation(element),
        "MotionLayout is missing the required `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute"
      )
      return
    }

    val value = attr.value
    if (!value.startsWith("@xml/")) {
      context.report(
        ISSUE,
        attr,
        context.getValueLocation(attr),
        "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must reference a motion scene file with `@xml/...`"
      )
      return
    }

    val sceneName = value.substring("@xml/".length)
    if (sceneName.isEmpty()) {
      context.report(
        ISSUE,
        attr,
        context.getValueLocation(attr),
        "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must reference a valid motion scene file"
      )
      return
    }

    references.add(SceneReference(context, attr, sceneName))
  }

  override fun afterCheckRootProject(context: Context) {
    for (reference in references) {
      if (!sceneFileExists(reference.context, reference.name)) {
        reference.context.report(
          ISSUE,
          reference.attribute,
          reference.context.getValueLocation(reference.attribute),
          "Motion scene file `@xml/${reference.name}` does not exist"
        )
      }
    }
    references.clear()
  }

  private fun sceneFileExists(context: XmlContext, sceneName: String): Boolean {
    val resourceFolders = context.project.resourceFolders ?: return false
    return resourceFolders.any { folder ->
      File(folder, "xml/$sceneName.xml").exists()
    }
  }

  private data class SceneReference(
    val context: XmlContext,
    val attribute: Attr,
    val name: String
  )

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "MotionLayoutInvalidSceneFileReference",
      briefDescription = "Invalid MotionLayout scene file reference",
      explanation = "A motion scene file specifies the animations used in a `MotionLayout`. " +
        "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute is required and must reference " +
        "a valid motion scene XML file in `res/xml`.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(MotionLayoutDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}