package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner, XmlScanner {

    private val passwordRequestLocations = mutableListOf<Location>()
    private var hasDalMetaData = false
    private var manifestLocation: Location? = null

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements \
                string resource file that includes the `assetlinks.json` files to load must be \
                declared in the manifest using a `<meta-data>` element.
                See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
                Scope.MANIFEST_SCOPE
            )
        )

        private const val GET_PASSWORD_REQUEST = "androidx.credentials.GetPasswordRequest"
        private const val ASSET_STATEMENTS_NAME = "asset_statements"
    }

    override fun getApplicableConstructorTypes(): List<String>? = listOf(GET_PASSWORD_REQUEST)

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        passwordRequestLocations.add(context.getLocation(node))
    }

    override fun getApplicableElements(): Collection<String>? = listOf(SdkConstants.TAG_META_DATA)

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.file.name != SdkConstants.ANDROID_MANIFEST_XML) return

        if (manifestLocation == null) {
            manifestLocation = context.getLocation(element)
        }

        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        if (name == ASSET_STATEMENTS_NAME) {
            hasDalMetaData = true
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (passwordRequestLocations.isNotEmpty() && !hasDalMetaData) {
            val location = manifestLocation ?: passwordRequestLocations.first()
            context.report(
                ISSUE,
                location,
                "Missing Digital Asset Links declaration for Credential Manager password sign-in. " +
                        "Add `<meta-data android:name=\"asset_statements\" android:resource=\"@string/asset_statements\" />` " +
                        "to your AndroidManifest.xml <application> tag."
            )
        }
        passwordRequestLocations.clear()
        hasDalMetaData = false
        manifestLocation = null
    }
}