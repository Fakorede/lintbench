package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val PASSWORD_CREDENTIAL_CLASS =
            "androidx.credentials.PasswordCredential"
        private const val GET_PASSWORD_OPTION_CLASS =
            "androidx.credentials.GetPasswordOption"

        private const val KEY_USES_PASSWORD_CREDENTIAL = "usesPasswordCredential"
        private const val KEY_LOCATION = "location"

        private const val META_DATA_ASSET_STATEMENTS = "asset_statements"
        private const val ANDROID_ASSET_STATEMENTS =
            "androidx.credentials.DIGITAL_ASSET_LINK_ASSET_STATEMENTS"

        @JvmField
        val ISSUE =
            Issue.create(
                id = "CredManMissingDal",
                briefDescription = "Missing Digital Asset Link for Credential Manager",
                explanation =
                    """
                    When using password sign-in through Credential Manager, an asset statements \
                    string resource file that includes the `assetlinks.json` files to load must \
                    be declared in the manifest using a `<meta-data>` element.

                    Add a `<meta-data>` element to your manifest's `<application>` block with \
                    the name `androidx.credentials.DIGITAL_ASSET_LINK_ASSET_STATEMENTS` pointing \
                    to a string resource that contains the asset statements JSON.

                    See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal
                    """,
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        CredentialManagerDigitalAssetLinkDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
                    ),
                androidSpecific = true,
            )
    }

    override fun getApplicableConstructorTypes(): List<String> =
        listOf(PASSWORD_CREDENTIAL_CLASS, GET_PASSWORD_OPTION_CLASS)

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        val map = context.getPartialResults(ISSUE).map()
        if (!map.containsKey(KEY_USES_PASSWORD_CREDENTIAL)) {
            map.put(KEY_USES_PASSWORD_CREDENTIAL, true)
            val location = context.getLocation(node)
            map.put(KEY_LOCATION, location)
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // Check if any module uses password credentials
        var usesPasswordCredential = false
        var firstLocation: Location? = null

        for ((_, map) in partialResults) {
            if (map.getBoolean(KEY_USES_PASSWORD_CREDENTIAL) == true) {
                usesPasswordCredential = true
                if (firstLocation == null) {
                    firstLocation = map.getLocation(KEY_LOCATION)
                }
            }
        }

        if (!usesPasswordCredential) return

        // Check manifest for the required meta-data element
        val mainProject = context.project
        val mergedManifest = mainProject.mergedManifest ?: return
        val root = mergedManifest.documentElement ?: return

        val applicationElements = root.getElementsByTagName(SdkConstants.TAG_APPLICATION)
        if (applicationElements.length == 0) {
            reportMissing(context, firstLocation)
            return
        }

        val applicationElement = applicationElements.item(0)
        val children = applicationElement.childNodes
        var found = false

        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeName == SdkConstants.TAG_META_DATA) {
                val nameAttr =
                    child.attributes?.getNamedItemNS(
                        SdkConstants.ANDROID_URI,
                        SdkConstants.ATTR_NAME,
                    )
                        ?: child.attributes?.getNamedItem("android:name")
                if (nameAttr?.nodeValue == ANDROID_ASSET_STATEMENTS ||
                    nameAttr?.nodeValue == META_DATA_ASSET_STATEMENTS
                ) {
                    found = true
                    break
                }
            }
        }

        if (!found) {
            reportMissing(context, firstLocation)
        }
    }

    override fun afterCheckRootProject(context: Context) {
        if (context.isGlobalAnalysis()) {
            checkPartialResults(context, context.getPartialResults(ISSUE))
        }
    }

    private fun reportMissing(context: Context, location: Location?) {
        val message =
            "When using password sign-in through Credential Manager, you must declare an " +
                "`asset_statements` `<meta-data>` element in the manifest's `<application>` " +
                "block with the name `androidx.credentials.DIGITAL_ASSET_LINK_ASSET_STATEMENTS` " +
                "pointing to a string resource containing the Digital Asset Links JSON. " +
                "See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal"

        val incident =
            Incident(
                ISSUE,
                location ?: Location.create(context.file),
                message,
            )
        context.report(incident)
    }
}