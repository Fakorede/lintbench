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

  private val sceneReferences = mutableListOf<SceneReference>()

  override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
    return folderType == com.android.resources.ResourceFolderType.LAYOUT
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf(
      "androidx.constraintlayout.motion.widget.MotionLayout",
      "MotionLayout"
    )
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val attr = element.findLayoutDescription() ?: run {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "MotionLayout must specify a motion scene file using `app:layoutDescription`"
      )
      return
    }

    val value = attr.value ?: return
    if (!value.startsWith("@xml/")) {
      context.report(
        ISSUE,
        attr,
        context.getValueLocation(attr),
        "`app:layoutDescription` must reference an XML resource in `res/xml`"
      )
      return
    }

    sceneReferences.add(SceneReference(context, attr))
  }

  override fun afterCheckRootProject(context: Context) {
    for (ref in sceneReferences) {
      val value = ref.attribute.value ?: continue
      val name = value.substring("@xml/".length)

      if (name.isEmpty()) {
        ref.context.report(
          ISSUE,
          ref.attribute,
          ref.context.getValueLocation(ref.attribute),
          "`app:layoutDescription` must specify a valid scene file name"
        )
        continue
      }

      val exists = context.project.resourceFolders.any { resFolder ->
        resFolder.listFiles()?.any { dir ->
          dir.isDirectory && dir.name.startsWith("xml") &&
            java.io.File(dir, "$name.xml").exists()
        } ?: false
      }

      if (!exists) {
        ref.context.report(
          ISSUE,
          ref.attribute,
          ref.context.getValueLocation(ref.attribute),
          "The motion scene file `$name.xml` referenced by `app:layoutDescription` was not found"
        )
      }
    }

    sceneReferences.clear()
  }

  private fun org.w3c.dom.Element.findLayoutDescription(): org.w3c.dom.Attr? {
    val attributes = this.attributes
    for (i in 0 until attributes.length) {
      val attr = attributes.item(i) as? org.w3c.dom.Attr ?: continue
      if (attr.localName == "layoutDescription") {
        return attr
      }
    }
    return null
  }

  private data class SceneReference(
    val context: XmlContext,
    val attribute: org.w3c.dom.Attr
  )

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "MotionLayoutInvalidSceneFileReference",
      briefDescription = "Invalid MotionLayout scene file reference",
      explanation = "A `MotionLayout` requires a valid motion scene file specified via the " +
        "`app:layoutDescription` attribute. The value must reference an XML resource in " +
        "`res/xml` and the referenced file must exist.",
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