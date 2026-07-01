package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.Context
import org.w3c.dom.Element

class CredentialManagerDigitalAssetLinkDetector : Detector(), Detector.XmlScanner {

    private var applicationElement: Element? = null
    private var hasAssetStatements: Boolean = false

    override fun getApplicableElements(): Collection<String> = listOf("application", "meta-data")

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.localName) {
            "application" -> {
                applicationElement = element
                hasAssetStatements = false
            }
            "meta-data" -> {
                val parentName = element.parentNode?.localName
                if (parentName == "application") {
                    val name = element.getAttributeNS(ANDROID_URI, "name")
                    val resource = element.getAttributeNS(ANDROID_URI, "resource")
                    if (name == "asset_statements" && resource.isNotBlank()) {
                        hasAssetStatements = true
                    }
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (applicationElement != null && !hasAssetStatements) {
            val xmlContext = context as XmlContext
            xmlContext.report(
                ISSUE,
                xmlContext.getElementLocation(applicationElement!!),
                "Credential Manager password sign-in requires an asset_statements <meta-data> declaration in <application>"
            )
        }
        applicationElement = null
        hasAssetStatements = false
    }

    companion object {
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, you must declare a Digital Asset Link
                by adding a `<meta-data>` element inside the `<application>` tag with
                `android:name="asset_statements"` and `android:resource="@string/..."` pointing to a
                string resource that contains your assetlinks.json statement.
            """,
            moreInfo = "https://developer.android.com/identity/sign-in/credential-manager#add-support-dal",
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