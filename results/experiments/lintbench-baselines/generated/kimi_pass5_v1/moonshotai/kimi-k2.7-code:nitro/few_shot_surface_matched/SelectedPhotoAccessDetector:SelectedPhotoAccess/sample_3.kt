package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
private const val ATTR_NAME = "name"
private const val TAG_USES_PERMISSION = "uses-permission"
private const val TAG_USES_SDK = "uses-sdk"
private const val PERMISSION_READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
private const val PERMISSION_READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
private const val PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED =
  "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

  private val photoAccessPermissions = mutableListOf<Location>()
  private var hasUserSelectedPermission = false
  private var targetSdkVersion = -1

  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_USES_PERMISSION, TAG_USES_SDK)
  }

  override fun beforeCheckProject(context: Context) {
    photoAccessPermissions.clear()
    hasUserSelectedPermission = false
    targetSdkVersion = -1
  }

  override fun visitElement(context: XmlContext, element: Element) {
    when (element.tagName) {
      TAG_USES_SDK -> {
        val target = element.getAttributeNS(ANDROID_URI, "targetSdkVersion")
        if (target.isNotEmpty()) {
          targetSdkVersion = target.toIntOrNull() ?: -1
        }
      }
      TAG_USES_PERMISSION -> {
        val attribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return
        when (attribute.value) {
          PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED -> hasUserSelectedPermission = true
          PERMISSION_READ_MEDIA_IMAGES, PERMISSION_READ_MEDIA_VIDEO -> {
            photoAccessPermissions.add(context.getValueLocation(attribute))
          }
        }
      }
    }
  }

  override fun checkMergedProject(context: Context) {
    if (targetSdkVersion < 34) {
      targetSdkVersion = context.mainProject.targetSdkVersion.apiLevel
    }
    if (targetSdkVersion < 34) return

    if (hasUserSelectedPermission || photoAccessPermissions.isEmpty()) return

    for (location in photoAccessPermissions) {
      context.report(
        ISSUE,
        location,
        "Selected Photo Access: when requesting READ_MEDIA_IMAGES or READ_MEDIA_VIDEO " +
          "on Android 14+, users can grant access to only a subset of their photos and videos. " +
          "Declare READ_MEDIA_VISUAL_USER_SELECTED and handle partial access, or use the Android photo picker."
      )
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "SelectedPhotoAccess",
      briefDescription = "Behavior change when requesting photo library access",
      explanation = "Selected Photo Access is a new ability for users to share partial access " +
        "to their photo library when apps request access to their device storage on Android 14+. " +
        "Instead of letting the system manage the selection lifecycle, you should adapt your app " +
        "to handle partial access to the photo library. See " +
        "https://developer.android.com/about/versions/14/changes/partial-photo-video-access.",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(
        SelectedPhotoAccessDetector::class.java,
        Scope.MANIFEST_SCOPE
      )
    )
  }
}