package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For targetSdkVersion >= 34, each `foregroundServiceType` listed in the `<service>` element \
                requires specific sets of permissions to be declared in the manifest. If permissions are \
                missing, then when the foreground service is started with a `foregroundServiceType` that has \
                missing permissions, a `SecurityException` will be thrown.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )

        private val TYPE_TO_PERMISSION = mapOf(
            "camera" to "android.permission.FOREGROUND_SERVICE_CAMERA",
            "connectedDevice" to "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
            "dataSync" to "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            "health" to "android.permission.FOREGROUND_SERVICE_HEALTH",
            "location" to "android.permission.FOREGROUND_SERVICE_LOCATION",
            "mediaPlayback" to "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            "mediaProjection" to "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
            "microphone" to "android.permission.FOREGROUND_SERVICE_MICROPHONE",
            "phoneCall" to "android.permission.FOREGROUND_SERVICE_PHONE_CALL",
            "remoteMessaging" to "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING",
            "specialUse" to "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            "systemExempted" to "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val targetSdkVersion = context.project.targetSdkVersion
        if (targetSdkVersion.featureLevel < 34) {
            return
        }

        val declaredPermissions = mutableSetOf<String>()
        val usesPermissions = document.getElementsByTagName(TAG_USES_PERMISSION)
        for (i in 0 until usesPermissions.length) {
            val element = usesPermissions.item(i) as? Element ?: continue
            val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name.isNotEmpty()) {
                declaredPermissions.add(name)
            }
        }

        val services = document.getElementsByTagName(TAG_SERVICE)
        for (i in 0 until services.length) {
            val service = services.item(i) as? Element ?: continue
            val fgsTypeAttr = service.getAttributeNodeNS(ANDROID_URI, SdkConstants.ATTR_FOREGROUND_SERVICE_TYPE) ?: continue
            val fgsType = fgsTypeAttr.value ?: continue
            if (fgsType.isEmpty()) continue

            val types = fgsType.split('|').map { it.trim() }
            for (type in types) {
                val requiredPermission = TYPE_TO_PERMISSION[type] ?: continue
                if (!declaredPermissions.contains(requiredPermission)) {
                    val location = context.getValueLocation(fgsTypeAttr)
                    context.report(
                        ISSUE,
                        fgsTypeAttr,
                        location,
                        "The foreground service type `$type` requires the `$requiredPermission` permission to be declared in the manifest for targetSdkVersion >= 34."
                    )
                }
            }
        }
    }
}