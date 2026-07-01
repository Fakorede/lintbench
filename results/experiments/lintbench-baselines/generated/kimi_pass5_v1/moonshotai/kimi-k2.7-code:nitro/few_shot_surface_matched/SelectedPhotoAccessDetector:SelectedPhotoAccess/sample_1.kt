package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_USES_PERMISSION)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val attribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return
    val permissionName = attribute.value
    if (permissionName !in AFFECTED_PERMISSIONS) {
      return
    }

    context.report(
      ISSUE,
      attribute,
      context.getValueLocation(attribute),
      "On Android 14 and higher, requesting this permission can result in users granting " +
        "selected access to photos and videos. Consider using the Photo Picker " +
        "(ACTION_PICK_IMAGES / Intent.ACTION_OPEN_DOCUMENT) instead."
    )
  }

  override fun checkMergedProject(context: Context) {
    super.checkMergedProject(context)
  }

  companion object {
    private val AFFECTED_PERMISSIONS = setOf(
      "android.permission.READ_EXTERNAL_STORAGE",
      "android.permission.READ_MEDIA_IMAGES",
      "android.permission.READ_MEDIA_VIDEO"
    )

    @JvmField
    val ISSUE = Issue.create(
      id = "SelectedPhotoAccess",
      briefDescription = "Selected Photo Access behavior change",
      explanation = "On Android 14 (API level 34) and higher, when an app requests " +
        "READ_MEDIA_IMAGES or READ_MEDIA_VIDEO, the system presents a dialog that lets the " +
        "user grant full access, deny access, or grant selected access to photos and videos. " +
        "If the user selects partial access, your app must handle it correctly. " +
        "Instead of relying on broad storage permissions, use the Photo Picker " +
        "(MediaStore.ACTION_PICK_IMAGES or Intent.ACTION_OPEN_DOCUMENT) so users can " +
        "choose specific media without granting broad access.",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(SelectedPhotoAccessDetector::class.java, Scope.MANIFEST_SCOPE),
    )
  }
}