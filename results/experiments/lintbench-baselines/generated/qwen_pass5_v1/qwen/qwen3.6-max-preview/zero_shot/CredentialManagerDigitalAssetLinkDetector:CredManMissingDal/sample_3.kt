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
import com.android.tools.lint.detector.api.LintUtils
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class CredentialManagerDigitalAssetLinkDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf(TAG_APPLICATION)

    override fun visitElement(context: XmlContext, element: Element) {
        val children = LintUtils.getChildren(element)
        var hasAssetStatements = false

        for (child in children) {
            if (child.tagName == TAG_META_DATA) {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name == "asset_statements") {
                    val resource = child.getAttributeNS(ANDROID_URI, ATTR_RESOURCE)
                    if (resource.isNotEmpty()) {
                        hasAssetStatements = true
                        break
                    }
                }
            }
        }

        if (!hasAssetStatements) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Missing Digital Asset Links configuration for Credential Manager. " +
                    "Add `<meta-data android:name=\"asset_statements\" android:resource=\"@string/asset_statements\" />` to the <application> element."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = "When using password sign-in through Credential Manager, an asset statements string resource file " +
                "that includes the `assetlinks.json` files to load must be declared in the manifest using a `<meta-data>` element.\n\n" +
                "Reference: https://developer.android.com/identity/sign-in/credential-manager#add-support-dal",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}