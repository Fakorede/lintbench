package com.android.tools.lint.checks

import com.android.SdkConstants.*
import com.android.annotations.VisibleForTesting
import com.android.resources.Density
import org.w3c.dom.Element
import org.xml.sax.Locator
import java.util.Locale
import com.android.tools.lint.detector.api.*
import com.android.utils.XmlUtils

class ForegroundServicePermissionDetector : Detector(), ManifestFilter {

    companion object Issues {
        val FOREGROUND_SERVICE_PERMISSION = Issue.create(
            "ForegroundServicePermission",
            "Missing required permissions for foregroundServiceType in the manifest.",
            """
                Each `foregroundServiceType` listed in the `<service>` element requires specific sets of permissions to be declared in the manifest. If permissions are missing, a `SecurityException` will be thrown when the service is started.
            """.trimIndent(),
            Severity.ERROR,
            Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun visitManifest(context: XmlContext) {
        val manifest = context.manifest
        val targetSdkVersion = getTargetSdkVersion(manifest)
        if (targetSdkVersion < 34) return

        val services = XmlUtils.getElementsByTagName(manifest.ownerDocument, TAG_SERVICE, manifest)
        for (i in 0 until services.length) {
            val serviceElement = services.item(i) as Element
            val foregroundServiceTypeAttr = serviceElement.getAttributeNS(null, ATTRIBUTE_FOREGROUND_SERVICE_TYPE)
            if (!foregroundServiceTypeAttr.isNullOrEmpty()) {
                val requiredPermissions = getRequiredPermissions(foregroundServiceTypeAttr)
                for (permission in requiredPermissions) {
                    if (!isPermissionDeclared(context, permission)) {
                        context.report(
                            FOREGROUND_SERVICE_PERMISSION,
                            serviceElement,
                            context.getLocation(serviceElement),
                            "Missing required permission $permission for foregroundServiceType: $foregroundServiceTypeAttr"
                        )
                    }
                }
            }
        }
    }

    private fun getTargetSdkVersion(manifest: Element): Int {
        val targetSdkVersion = manifest.getAttributeNS(null, ATTRIBUTE_TARGET_SDK_VERSION)
        return try {
            Integer.parseInt(targetSdkVersion)
        } catch (e: NumberFormatException) {
            -1
        }
    }

    @VisibleForTesting
    internal fun getRequiredPermissions(foregroundServiceTypeAttr: String): List<String> {
        val types = foregroundServiceTypeAttr.split(',')
        val requiredPermissions = mutableListOf<String>()
        for (type in types) {
            when (type.trim().toLowerCase(Locale.US)) {
                "location" -> requiredPermissions.add("android.permission.FOREGROUND_SERVICE_LOCATION")
                "camera" -> requiredPermissions.add("android.permission.FOREGROUND_SERVICE_CAMERA")
                else -> {} // Ignore unknown types
            }
        }
        return requiredPermissions.distinct()
    }

    private fun isPermissionDeclared(context: XmlContext, permission: String): Boolean {
        val manifest = context.manifest
        val permissions = XmlUtils.getElementsByTagName(manifest.ownerDocument, TAG_PERMISSION, manifest)
        for (i in 0 until permissions.length) {
            val permissionElement = permissions.item(i) as Element
            if (permissionElement.getAttributeNS(null, ATTRIBUTE_NAME).equals(permission)) {
                return true
            }
        }
        return false
    }
}