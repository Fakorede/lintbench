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
    private var dalPresent = false
    private var manifestLocation: Location? = null

    companion object {
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
            dalPresent = true
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (credManUsed && !dalPresent) {
            val location = manifestLocation ?: Location.create(context.project.dir)
            context.report(
                ISSUE,
                location,
                "Missing Digital Asset Links meta-data. When using Credential Manager for password sign-in, " +
                "you must declare `<meta-data android:name=\"asset_statements\" android:resource=\"@string/asset_statements\" />` " +
                "in your AndroidManifest.xml."
            )
        }

        credManUsed = false
        dalPresent = false
        manifestLocation = null
    }
}