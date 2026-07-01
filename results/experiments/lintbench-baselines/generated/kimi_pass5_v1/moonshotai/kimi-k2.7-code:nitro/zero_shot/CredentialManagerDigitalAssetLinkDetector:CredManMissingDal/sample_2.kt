package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_RESOURCE
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_META_DATA
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class CredentialManagerDigitalAssetLinkDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String> = setOf(TAG_APPLICATION)

    override fun visitElement(context: XmlContext, element: Element) {
        if (hasAssetLinkMetaData(element)) return

        val message = "Missing Digital Asset Link declaration for Credential Manager. " +
            "Add a `<meta-data>` element to `<application>` with " +
            "`android:name=\"assetlinks\"` and `android:resource=\"@string/assetlinks\"`."

        context.report(
            ISSUE,
            context.getElementLocation(element),
            message
        )
    }

    private fun hasAssetLinkMetaData(application: Element): Boolean {
        val metaDataElements = application.getElementsByTagName(TAG_META_DATA)
        for (i in 0 until metaDataElements.length) {
            val child = metaDataElements.item(i) as? Element ?: continue
            val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
            val resource = child.getAttributeNS(ANDROID_URI, ATTR_RESOURCE)
            if (name == "assetlinks" && resource.isNotBlank()) {
                return true
            }
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, you must declare an asset
                statements string resource that includes the assetlinks.json files to load.
                Declare this in the manifest by adding a `<meta-data>` element to the
                `<application>` element with `android:name="assetlinks"` and
                `android:resource="@string/assetlinks"`.

                See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal
            """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}