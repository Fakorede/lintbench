/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
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
import java.util.EnumSet

/**
 * Detector that warns when apps request READ_MEDIA_IMAGES or READ_MEDIA_VIDEO permissions
 * without also handling the new READ_MEDIA_VISUAL_USER_SELECTED permission introduced in
 * Android 14 for Selected Photo Access (partial photo library access).
 */
class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    companion object {

        private const val PERMISSION_READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val PERMISSION_READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
        private const val PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED =
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

        private const val TAG_USES_PERMISSION = "uses-permission"

        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                Selected Photo Access is a new ability for users to share partial access to \
                their photo library when apps request access to their device storage on \
                Android 14+.

                Instead of letting the system manage the selection lifecycle, we recommend \
                you adapt your app to handle partial access to the photo library.

                To handle this properly, your app should also request the \
                `READ_MEDIA_VISUAL_USER_SELECTED` permission alongside \
                `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO`. This allows your app to \
                gracefully handle the case where users grant only partial access to \
                their photo library.

                Reference: https://developer.android.com/about/versions/14/changes/partial-photo-video-access
                """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                SelectedPhotoAccessDetector::class.java,
                EnumSet.of(Scope.MANIFEST)
            ),
            androidSpecific = true
        ).addMoreInfo(
            "https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
        )
    }

    /** Tracks whether the manifest declares READ_MEDIA_IMAGES or READ_MEDIA_VIDEO. */
    private var hasReadMediaImages = false
    private var hasReadMediaVideo = false

    /** Tracks whether the manifest declares READ_MEDIA_VISUAL_USER_SELECTED. */
    private var hasReadMediaVisualUserSelected = false

    /** Stores the elements for READ_MEDIA_IMAGES and READ_MEDIA_VIDEO for reporting. */
    private val mediaPermissionElements = mutableListOf<Pair<XmlContext, Element>>()

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_PERMISSION)
    }

    override fun beforeCheckFile(context: Context) {
        // Reset state for each manifest file
        hasReadMediaImages = false
        hasReadMediaVideo = false
        hasReadMediaVisualUserSelected = false
        mediaPermissionElements.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val permissionName = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return

        when (permissionName) {
            PERMISSION_READ_MEDIA_IMAGES -> {
                hasReadMediaImages = true
                mediaPermissionElements.add(Pair(context, element))
            }
            PERMISSION_READ_MEDIA_VIDEO -> {
                hasReadMediaVideo = true
                mediaPermissionElements.add(Pair(context, element))
            }
            PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED -> {
                hasReadMediaVisualUserSelected = true
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        // Only report if the app requests media permissions but NOT the user-selected permission
        if ((hasReadMediaImages || hasReadMediaVideo) && !hasReadMediaVisualUserSelected) {
            for ((xmlContext, element) in mediaPermissionElements) {
                val permissionName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                xmlContext.report(
                    issue = ISSUE,
                    location = xmlContext.getNameLocation(element),
                    message = "To handle the `READ_MEDIA_VISUAL_USER_SELECTED` permission that " +
                        "is available on Android 14+, you should also request the " +
                        "`$PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED` permission alongside " +
                        "`$permissionName`. This allows your app to handle partial photo " +
                        "library access granted by the user."
                )
            }
        }
    }
}