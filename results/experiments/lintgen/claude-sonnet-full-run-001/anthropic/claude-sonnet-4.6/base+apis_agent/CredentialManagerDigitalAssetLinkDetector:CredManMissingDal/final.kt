package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_META_DATA
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), XmlScanner {

    companion object {
        private const val META_DATA_ASSET_STATEMENTS = "asset_statements"
        private const val META_DATA_ASSET_STATEMENTS_FULL = "androidx.credentials.ASSET_STATEMENTS"

        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements \
                string resource file that includes the `assetlinks.json` files to load must be \
                declared in the manifest using a `<meta-data>` element.

                Add a `<meta-data>` element inside the `<application>` tag in your \
                `AndroidManifest.xml` that points to a string resource containing the Digital \
                Asset Links JSON.

                See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal \
                for more details.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.MANIFEST)
            ),
            moreInfo = "https://developer.android.com/identity/sign-in/credential-manager#add-support-dal"
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_APPLICATION, TAG_META_DATA)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_APPLICATION -> {
                // Check if the application element has the required asset_statements meta-data
                checkApplicationForAssetStatements(context, element)
            }
            TAG_META_DATA -> {
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return
                if (isAssetStatementsMetaData(name)) {
                    // Validate the meta-data has a proper value attribute
                    val value = element.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                    if (value.isNullOrBlank()) {
                        context.report(
                            issue = ISSUE,
                            scope = element,
                            location = context.getLocation(element),
                            message = "The `asset_statements` `<meta-data>` element must have an " +
                                "`android:value` attribute pointing to a string resource that " +
                                "lists the `assetlinks.json` files to load."
                        )
                    }
                }
            }
        }
    }

    private fun isAssetStatementsMetaData(name: String): Boolean {
        return name == META_DATA_ASSET_STATEMENTS ||
            name == META_DATA_ASSET_STATEMENTS_FULL
    }

    private fun checkApplicationForAssetStatements(context: XmlContext, application: Element) {
        var hasAssetStatements = false
        val children = application.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == TAG_META_DATA) {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: continue
                if (isAssetStatementsMetaData(name)) {
                    hasAssetStatements = true
                    break
                }
            }
        }

        if (!hasAssetStatements) {
            context.report(
                issue = ISSUE,
                scope = application,
                location = context.getLocation(application),
                message = "Missing `asset_statements` `<meta-data>` element in `<application>`. " +
                    "When using Credential Manager for password sign-in, you must declare an " +
                    "`asset_statements` `<meta-data>` element pointing to a string resource " +
                    "that lists the `assetlinks.json` files to load. " +
                    "See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal"
            )
        }
    }
}