package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_META_DATA
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class CredentialManagerDigitalAssetLinkDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = "When using password sign-in through Credential Manager, an asset statements string resource file that includes the `assetlinks.json` files to load must be declared in the manifest using a `<meta-data>` element.\n\nSee https://developer.android.com/identity/sign-in/credential-manager#add-support-dal",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String> = listOf(TAG_APPLICATION)

    override fun visitElement(context: XmlContext, element: Element) {
        var hasAssetStatements = false
        var child: Node? = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE && TAG_META_DATA == child.nodeName) {
                val metaDataElement = child as Element
                val name = metaDataElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if ("asset_statements" == name) {
                    hasAssetStatements = true
                    break
                }
            }
            child = child.nextSibling
        }

        if (!hasAssetStatements) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Missing `<meta-data android:name=\"asset_statements\" ...>` declaration required for Credential Manager password sign-in."
            )
        }
    }
}