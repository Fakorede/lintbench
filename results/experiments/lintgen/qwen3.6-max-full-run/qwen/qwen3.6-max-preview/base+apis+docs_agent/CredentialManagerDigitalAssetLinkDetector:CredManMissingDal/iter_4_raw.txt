package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner, XmlScanner {

    private var credManUsed = false
    private var dalMetaDataFound = false
    private var dalResourceValid = false
    private var manifestLocation: Location? = null
    private var dalMetaDataElement: Element? = null
    private var missingResourceAttr = false
    private var missingInclude = false
    private var missingUrl = false

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements \
                string resource file that includes the `assetlinks.json` files to load must \
                be declared in the manifest using a `<meta-data>` element.
                See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)
            )
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? {
        return listOf(
            "androidx.credentials.GetPasswordRequest",
            "androidx.credentials.GetCredentialRequest"
        )
    }

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        credManUsed = true
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(SdkConstants.TAG_META_DATA)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.file.name != SdkConstants.ANDROID_MANIFEST_XML) return

        if (manifestLocation == null) {
            manifestLocation = context.getLocation(element)
        }

        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        if (name == "asset_statements") {
            dalMetaDataFound = true
            dalMetaDataElement = element
            val resourceAttr = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_RESOURCE)
            if (resourceAttr.isEmpty()) {
                missingResourceAttr = true
            } else {
                val value = context.getResourceValue(resourceAttr, false)
                if (value != null) {
                    if (!value.contains("include")) {
                        missingInclude = true
                    } else if (!value.contains("https://") && !value.contains("http://")) {
                        missingUrl = true
                    } else {
                        dalResourceValid = true
                    }
                }
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (!credManUsed) {
            resetState()
            return
        }

        if (!dalMetaDataFound) {
            context.report(
                ISSUE,
                manifestLocation ?: Location.create(context.project.dir),
                "Missing Digital Asset Links meta-data. When using Credential Manager for password sign-in, " +
                "you must declare `<meta-data android:name=\"asset_statements\" android:resource=\"@string/asset_statements\" />` " +
                "in your AndroidManifest.xml."
            )
        } else if (missingResourceAttr) {
            context.report(
                ISSUE,
                context.getLocation(dalMetaDataElement!!),
                "Missing `android:resource` attribute. The `asset_statements` meta-data must reference a string resource containing the Digital Asset Links."
            )
        } else if (missingInclude) {
            context.report(
                ISSUE,
                context.getLocation(dalMetaDataElement!!),
                "Missing `include` directive in asset statements. The string resource must contain a JSON object with an `\"include\"` key pointing to your `assetlinks.json` URL."
            )
        } else if (missingUrl) {
            context.report(
                ISSUE,
                context.getLocation(dalMetaDataElement!!),
                "Missing or invalid URL in asset statements. The `include` value must be a valid HTTP/HTTPS URL pointing to your `assetlinks.json` file."
            )
        }

        resetState()
    }

    private fun resetState() {
        credManUsed = false
        dalMetaDataFound = false
        dalResourceValid = false
        manifestLocation = null
        dalMetaDataElement = null
        missingResourceAttr = false
        missingInclude = false
        missingUrl = false
    }
}