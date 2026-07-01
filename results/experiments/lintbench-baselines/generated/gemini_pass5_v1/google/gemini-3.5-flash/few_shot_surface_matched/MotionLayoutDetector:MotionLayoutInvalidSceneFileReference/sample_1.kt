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

  override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
    return folderType == com.android.resources.ResourceFolderType.LAYOUT
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf(
      "androidx.constraintlayout.motion.widget.MotionLayout",
      "android.support.constraint.MotionLayout"
    )
  }

  override fun afterCheckRootProject(context: Context) {
    super.afterCheckRootProject(context)
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val attribute = element.getAttributeNodeNS(
      com.android.SdkConstants.AUTO_URI,
      com.android.SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
    )

    if (attribute == null) {
      context.report(
        ISSUE,
        element,
        context.getNameLocation(element),
        "MotionLayout must specify a scene file using `app:layoutDescription`"
      )
      return
    }

    val value = attribute.value
    val isValid = value.startsWith("@xml/") || (value.startsWith("@") && value.contains(":xml/"))
    if (!isValid) {
      context.report(
        ISSUE,
        attribute,
        context.getValueLocation(attribute),
        "The `layoutDescription` attribute must specify a valid motion scene file (`@xml/...`)"
      )
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "MotionLayoutInvalidSceneFileReference",
      briefDescription = "MotionLayout layoutDescription must specify a scene file",
      explanation = "A motion scene file specifies the animations used in a `MotionLayout`. " +
        "The `layoutDescription` is required to specify a valid motion scene file.",
      category = Category.CORRECTNESS,
      priority = 8,
      severity = Severity.ERROR,
      implementation = Implementation(MotionLayoutDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}