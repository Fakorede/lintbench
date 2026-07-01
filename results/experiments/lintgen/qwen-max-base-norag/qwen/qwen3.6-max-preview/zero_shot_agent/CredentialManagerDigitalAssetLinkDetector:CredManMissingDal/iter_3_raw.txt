package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String>? = listOf(SdkConstants.TAG_META_DATA, SdkConstants.TAG_STRING)

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            SdkConstants.TAG_META_DATA -> checkMetaData(context, element)
            SdkConstants.TAG_STRING -> checkStringResource(context, element)
        }
    }

    private fun checkMetaData(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        if (name != "asset_statements") return

        val resourceAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_RESOURCE)
        if (resourceAttr == null || resourceAttr.value.isEmpty()) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Missing `android:resource` attribute for `asset_statements` meta-data. " +
                "Add `android:resource=\"@string/asset_statements\"` to reference the Digital Asset Links configuration."
            )
        }
    }

    private fun checkStringResource(context: XmlContext, element: Element) {
        val name = element.getAttribute(SdkConstants.ATTR_NAME)
        if (name != "asset_statements") return

        val text = element.textContent ?: return

        val hasInclude = text.contains("include")
        val hasRelation = text.contains("relation")
        val hasTarget = text.contains("target")

        if (!hasInclude && !(hasRelation && hasTarget)) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Missing `include` or `relation`/`target` in `asset_statements` JSON. " +
                "Ensure the string resource contains valid Digital Asset Links configuration."
            )
            return
        }

        val urlRegex = Regex("https?://[^\\s\"}]+")
        if (!urlRegex.containsMatchIn(text)) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Missing URL in `asset_statements` JSON. " +
                "Ensure the configuration includes a valid HTTPS URL for the asset links."
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
            severity = Severity.ERROR,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE)
            )
        )
    }
}