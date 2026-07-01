package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class ForegroundServicePermissionDetector : ResourceXmlDetector() {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val TARGET_SDK_THRESHOLD = 34

        private val IMPLEMENTATION = Implementation(
            ForegroundServicePermissionDetector::class.java,
            EnumSet.of(Scope.MANIFEST),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                Starting with Android 14 (targetSdkVersion 34), every foreground service that
                declares a `foregroundServiceType` must also declare the matching permission in
                the manifest. If the required permission is missing, a `SecurityException` is
                thrown when the service is started with that type.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
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
            "shortService" to "android.permission.FOREGROUND_SERVICE",
            "specialUse" to "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            "systemExposed" to "android.permission.FOREGROUND_SERVICE_SYSTEM_EXPOSED",
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("service")

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val manifest = context.document.documentElement ?: return
        if (getTargetSdk(context, manifest) < TARGET_SDK_THRESHOLD) return

        val declaredPermissions = getDeclaredPermissions(manifest)
        for (type in getForegroundServiceTypes(element)) {
            val requiredPermission = TYPE_TO_PERMISSION[type] ?: continue
            if (requiredPermission !in declaredPermissions) {
                val message =
                    "Missing permission `$requiredPermission` required for foregroundServiceType=\"$type\""
                context.report(ISSUE, Location.create(context.file, element), message)
            }
        }
    }

    private fun getTargetSdk(context: XmlContext, manifest: org.w3c.dom.Element): Int {
        val nodes = manifest.getElementsByTagName("uses-sdk")
        if (nodes.length > 0) {
            val usesSdk = nodes.item(0) as? org.w3c.dom.Element
            if (usesSdk != null) {
                val target = usesSdk.getAttributeNS(ANDROID_URI, "targetSdkVersion")
                if (target.isNotEmpty()) {
                    return target.toIntOrNull() ?: context.project.targetSdkVersion
                }
                val min = usesSdk.getAttributeNS(ANDROID_URI, "minSdkVersion")
                if (min.isNotEmpty()) {
                    return min.toIntOrNull() ?: context.project.targetSdkVersion
                }
            }
        }
        return context.project.targetSdkVersion
    }

    private fun getDeclaredPermissions(manifest: org.w3c.dom.Element): Set<String> {
        val permissions = mutableSetOf<String>()
        val nodes = manifest.getElementsByTagName("uses-permission")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? org.w3c.dom.Element ?: continue
            val name = node.getAttributeNS(ANDROID_URI, "name")
            if (name.isNotEmpty()) permissions.add(name)
        }
        return permissions
    }

    private fun getForegroundServiceTypes(service: org.w3c.dom.Element): List<String> {
        val value = service.getAttributeNS(ANDROID_URI, "foregroundServiceType")
        if (value.isEmpty()) return emptyList()
        return value.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    }
}