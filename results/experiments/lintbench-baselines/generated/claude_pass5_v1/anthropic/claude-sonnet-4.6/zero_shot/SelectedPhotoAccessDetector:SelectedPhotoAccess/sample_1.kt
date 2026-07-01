package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element
import java.util.EnumSet

class SelectedPhotoAccessDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
        private const val READ_MEDIA_VISUAL_USER_SELECTED =
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

        private const val ATTR_NAME = "name"
        private const val TAG_USES_PERMISSION = "uses-permission"

        private const val KEY_HAS_READ_MEDIA_IMAGES = "hasReadMediaImages"
        private const val KEY_HAS_READ_MEDIA_VIDEO = "hasReadMediaVideo"
        private const val KEY_HAS_USER_SELECTED = "hasUserSelected"
        private const val KEY_LOCATION = "location"

        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                Selected Photo Access is a new ability for users to share partial access to \
                their photo library when apps request access to their device storage on \
                Android 14+.

                Instead of letting the system manage the selection lifecycle, we recommend \
                you adapt your app to handle partial access to the photo library.

                Reference documentation:
                https://developer.android.com/about/versions/14/changes/partial-photo-video-access
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                SelectedPhotoAccessDetector::class.java,
                EnumSet.of(Scope.MANIFEST),
                EnumSet.of(Scope.MANIFEST)
            ),
            androidSpecific = true
        )

        private const val ISSUE_MESSAGE =
            "If you need access to shared photos and videos, you should request " +
                "`READ_MEDIA_VISUAL_USER_SELECTED` in addition to `READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO` " +
                "to support the Photo Picker permission on Android 14+"
    }

    // Track permissions found in manifest
    private var hasReadMediaImages = false
    private var hasReadMediaVideo = false
    private var hasReadMediaVisualUserSelected = false
    private var readMediaImagesLocation: Location? = null
    private var readMediaVideoLocation: Location? = null

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_PERMISSION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return

        when (name) {
            READ_MEDIA_IMAGES -> {
                hasReadMediaImages = true
                if (readMediaImagesLocation == null) {
                    readMediaImagesLocation = context.getLocation(element)
                }
            }
            READ_MEDIA_VIDEO -> {
                hasReadMediaVideo = true
                if (readMediaVideoLocation == null) {
                    readMediaVideoLocation = context.getLocation(element)
                }
            }
            READ_MEDIA_VISUAL_USER_SELECTED -> {
                hasReadMediaVisualUserSelected = true
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (!hasReadMediaImages && !hasReadMediaVideo) {
            return
        }

        if (hasReadMediaVisualUserSelected) {
            return
        }

        // Report on the location of READ_MEDIA_IMAGES or READ_MEDIA_VIDEO
        val location = readMediaImagesLocation ?: readMediaVideoLocation ?: return

        val incident = Incident(
            issue = ISSUE,
            message = ISSUE_MESSAGE,
            location = location
        )

        context.report(incident)
    }

    override fun beforeCheckFile(context: Context) {
        hasReadMediaImages = false
        hasReadMediaVideo = false
        hasReadMediaVisualUserSelected = false
        readMediaImagesLocation = null
        readMediaVideoLocation = null
    }
}