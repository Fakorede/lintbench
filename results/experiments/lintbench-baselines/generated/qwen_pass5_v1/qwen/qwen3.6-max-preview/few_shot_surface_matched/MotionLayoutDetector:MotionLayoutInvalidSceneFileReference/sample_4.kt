package com.android.tools.lint.checks

import com.android.SdkConstants.AUTO_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class MotionLayoutDetector : ResourceXmlDetector(), XmlScanner {

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.LAYOUT
  }

  override fun getApplicableElements(): Collection<String>? {
    return listOf(
      "androidx.constraintlayout.motion.widget.MotionLayout",
      "android.support.constraint.motion.MotionLayout"
    )
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val attr = element.getAttributeNodeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
    if (attr == null) {
      context.report(
        ISSUE,
        element,
        context.getLocation(element),
        "MotionLayout must specify a motion scene file via app:$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION"
      )
      return
    }

    val value = attr.value
    if (!value.startsWith("@xml/")) {
      context.report(
        ISSUE,
        attr,
        context.getValueLocation(attr),
        "app:$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION must reference a valid motion scene file in res/xml/"
      )
    }
  }

  override fun afterCheckRootProject(context: Context) {
    // No project-wide validation required for this check
  }

  companion object {
    private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"

    @JvmField
    val ISSUE = Issue.create(
      id = "MotionLayoutInvalidSceneFileReference",
      briefDescription = "MotionLayout must specify a valid scene file",
      explanation = "A motion scene file specifies the animations used in a MotionLayout. " +
        "The $ATTR_CONSTRAINT_LAYOUT_DESCRIPTION is required to specify a valid motion scene file.",
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