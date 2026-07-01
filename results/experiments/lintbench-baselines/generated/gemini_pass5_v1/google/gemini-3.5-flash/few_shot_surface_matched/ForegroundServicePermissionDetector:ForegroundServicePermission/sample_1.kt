package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class ForegroundServicePermissionDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf("service")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val project = context.project
        val targetSdk = project.targetSdkVersion.apiLevel
        if (targetSdk < 34) return

        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val fgsTypeAttr = element.getAttributeNodeNS(androidNamespace, "foregroundServiceType") ?: return
        val fgsTypeVal = fgsTypeAttr.value ?: return

        val declaredPermissions = mutableSetOf<String>()
        val root = context.document.documentElement
        if (root != null) {
            val usesPermissions = root.getElementsByTagName("uses-permission")
            for (i in 0 until usesPermissions.length) {
                val permissionEl = usesPermissions.item(i) as? org.w3c.dom.Element ?: continue
                val name = permissionEl.getAttributeNS(androidNamespace, "name")
                if (name.isNotEmpty()) {
                    declaredPermissions.add(name)
                }
            }
        }

        val types = fgsTypeVal.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        val missingPermissions = mutableListOf<String>()

        if (!declaredPermissions.contains("android.permission.FOREGROUND_SERVICE")) {
            missingPermissions.add("android.permission.FOREGROUND_SERVICE")
        }

        val serviceTypePermissions = mapOf(
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

        for (type in types) {
            val requiredPermission = serviceTypePermissions[type]
            if (requiredPermission != null && !declaredPermissions.contains(requiredPermission)) {
                missingPermissions.add(requiredPermission)
            }
        }

        if (missingPermissions.isNotEmpty()) {
            val missingList = missingPermissions.joinToString(", ")
            val message = "The `<service>` element has foreground service type(s) that require the following permission(s) to be declared in the manifest: $missingList"
            context.report(
                ISSUE,
                fgsTypeAttr,
                context.getValueLocation(fgsTypeAttr),
                message
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = "For targetSdkVersion >= 34, each `foregroundServiceType` listed in the `<service>` element requires specific sets of permissions to be declared in the manifest. If permissions are missing, then when the foreground service is started with a `foregroundServiceType` that has missing permissions, a `SecurityException` will be thrown.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}