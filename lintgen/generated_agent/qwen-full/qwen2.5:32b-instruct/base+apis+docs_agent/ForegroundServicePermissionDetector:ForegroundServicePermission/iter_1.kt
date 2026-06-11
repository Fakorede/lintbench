package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : Detector(), XmlScanner {

    companion object {
        private val REQUIRED_PERMISSIONS = mapOf(
            "FOREGROUND_SERVICE_LOCATION" to listOf("android.permission.ACCESS_FINE_LOCATION"),
            "FOREGROUND_SERVICE_MEDIA_PROJECTION" to listOf("android.permission.MEDIA_CONTENT_CONTROL")
        )

        private val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For targetSdkVersion >= 34, each `foregroundServiceType` listed in the `<service>` element requires specific sets of permissions to be declared in the manifest. If permissions are missing, then when the foreground service is started with a `foregroundServiceType` that has missing permissions, a `SecurityException` will be thrown.
            """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("service")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdkVersion = context.getManifest().targetSdkVersion
        if (targetSdkVersion >= 34) {
            val foregroundServiceTypes = getForegroundServiceTypes(element)
            if (foregroundServiceTypes.isNotEmpty()) {
                checkPermissions(context, element, foregroundServiceTypes)
            }
        }
    }

    private fun getForegroundServiceTypes(serviceElement: Element): List<String> {
        return serviceElement.getAttribute("android:foregroundServiceType").split(",")
    }

    private fun checkPermissions(context: XmlContext, serviceElement: Element, foregroundServiceTypes: List<String>) {
        val manifest = context.getManifest()
        val declaredPermissions = manifest.permissions.map { it.name }
        
        for (serviceType in foregroundServiceTypes) {
            val requiredPermissions = REQUIRED_PERMISSIONS[serviceType.trim()]
            if (requiredPermissions != null) {
                for (permission in requiredPermissions) {
                    if (!declaredPermissions.contains(permission)) {
                        context.report(
                            ISSUE,
                            context.getLocation(serviceElement),
                            "Missing permission $permission for foregroundServiceType $serviceType"
                        )
                    }
                }
            }
        }
    }
}