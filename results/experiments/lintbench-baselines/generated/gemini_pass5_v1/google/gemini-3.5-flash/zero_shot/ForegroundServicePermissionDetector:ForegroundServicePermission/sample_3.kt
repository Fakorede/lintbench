package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : Detector(), Detector.XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For targetSdkVersion >= 34, each `foregroundServiceType` listed in the `<service>` element \
                requires specific sets of permissions to be declared in the manifest. If permissions are \
                missing, then when the foreground service is started with a `foregroundServiceType` that has \
                missing permissions, a `SecurityException` will be thrown.
            """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )

        private val SERVICE_TYPE_TO_PERMISSION = mapOf(
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
        val root = document.documentElement ?: return

        var targetSdk = context.project.targetSdkVersion.featureLevel
        if (targetSdk < 0) {
            val usesSdk = root.getElementsByTagName(SdkConstants.TAG_USES_SDK)
            if (usesSdk.length > 0) {
                val usesSdkElement = usesSdk.item(0) as? Element
                val targetSdkStr = usesSdkElement?.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TARGET_SDK_VERSION)
                targetSdkStr?.toIntOrNull()?.let {
                    targetSdk = it
                }
            }
        }

        if (targetSdk > 0 && targetSdk < 34) {
            return
        }

        val declaredPermissions = mutableSetOf<String>()
        
        val usesPermissions = root.getElementsByTagName(SdkConstants.TAG_USES_PERMISSION)
        for (i in 0 until usesPermissions.length) {
            val item = usesPermissions.item(i) as? Element ?: continue
            val name = item.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name.isNotEmpty()) {
                declaredPermissions.add(name)
            }
        }

        val usesPermissionsSdk23 = root.getElementsByTagName("uses-permission-sdk-23")
        for (i in 0 until usesPermissionsSdk23.length) {
            val item = usesPermissionsSdk23.item(i) as? Element ?: continue
            val name = item.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name.isNotEmpty()) {
                declaredPermissions.add(name)
            }
        }

        val services = root.getElementsByTagName(SdkConstants.TAG_SERVICE)
        for (i in 0 until services.length) {
            val service = services.item(i) as? Element ?: continue
            val serviceTypeAttr = service.getAttributeNS(SdkConstants.ANDROID_URI, "foregroundServiceType")
            if (serviceTypeAttr.isNullOrEmpty()) {
                continue
            }

            val types = serviceTypeAttr.split('|').map { it.trim() }
            for (type in types) {
                val requiredPermission = SERVICE_TYPE_TO_PERMISSION[type] ?: continue
                if (!declaredPermissions.contains(requiredPermission)) {
                    val attrNode = service.getAttributeNodeNS(SdkConstants.ANDROID_URI, "foregroundServiceType")
                    val location = if (attrNode != null) context.getValueLocation(attrNode) else context.getLocation(service)
                    context.report(
                        ISSUE,
                        service,
                        location,
                        "The service has foregroundServiceType \"$type\" but does not declare the required permission $requiredPermission"
                    )
                }
            }
        }
    }
}