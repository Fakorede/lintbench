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
        private const val KEY_LOCATION = "location"
        private const val KEY_HAS_DAL = "hasDal"

        private const val PASSWORD_CREDENTIAL_CLASS =
            "androidx.credentials.PasswordCredential"
        private const val GET_PASSWORD_OPTION_CLASS =
            "androidx.credentials.GetPasswordOption"
        private const val CREATE_PASSWORD_REQUEST_CLASS =
            "androidx.credentials.CreatePasswordRequest"

        private const val ASSET_STATEMENTS_META_DATA =
            "asset_statements"

        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation =
                """
                When using password sign-in through Credential Manager, an asset statements \
                string resource file that includes the `assetlinks.json` files to load must be \
                declared in the manifest using a `<meta-data>` element.

                Please add a `<meta-data>` element to your manifest with the name \
                `asset_statements` pointing to a string resource that lists the Digital Asset \
                Links JSON files. See \
                https://developer.android.com/identity/sign-in/credential-manager#add-support-dal \
                for details.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
            ),
            androidSpecific = true,
        )

        private val CREDENTIAL_MANAGER_CLASSES = listOf(
            PASSWORD_CREDENTIAL_CLASS,
            GET_PASSWORD_OPTION_CLASS,
            CREATE_PASSWORD_REQUEST_CLASS,
        )
    }

    override fun getApplicableConstructorTypes(): List<String> = CREDENTIAL_MANAGER_CLASSES

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        val location = context.getLocation(node)
        context.getPartialResults(ISSUE).map().apply {
            // Record that we found a usage; store location of first usage if not already set
            val existingLocation = this[KEY_LOCATION]
            if (existingLocation == null) {
                put(KEY_LOCATION, location)
            }
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // Check the manifest for the asset_statements meta-data element
        val mainProject = context.mainProject
        val mergedManifest = mainProject.mergedManifest ?: return
        val documentElement = mergedManifest.documentElement ?: return

        var hasAssetStatements = false

        // Walk through the manifest looking for <meta-data> elements with asset_statements
        val applicationNodes = documentElement.getElementsByTagName(SdkConstants.TAG_APPLICATION)
        for (i in 0 until applicationNodes.length) {
            val appNode = applicationNodes.item(i)
            val children = appNode.childNodes
            for (j in 0 until children.length) {
                val child = children.item(j)
                if (child.nodeName == SdkConstants.TAG_META_DATA) {
                    val nameAttr = child.attributes?.getNamedItemNS(
                        SdkConstants.ANDROID_URI,
                        SdkConstants.ATTR_NAME,
                    ) ?: child.attributes?.getNamedItem("android:name")
                    val nameValue = nameAttr?.nodeValue ?: continue
                    if (nameValue.contains(ASSET_STATEMENTS_META_DATA)) {
                        hasAssetStatements = true
                        break
                    }
                }
            }
            if (hasAssetStatements) break
        }

        // Aggregate results from all modules
        var foundUsage = false
        var firstLocation: Location? = null

        for (map: LintMap in partialResults) {
            val loc = map[KEY_LOCATION]
            if (loc != null) {
                foundUsage = true
                if (firstLocation == null) {
                    firstLocation = loc
                }
            }
        }

        if (foundUsage && !hasAssetStatements) {
            val location = firstLocation ?: Location.create(context.file)
            context.report(
                Incident(
                    ISSUE,
                    location,
                    "This app uses Credential Manager for password sign-in, but is missing a " +
                        "`<meta-data>` element in the manifest with `android:name=\"asset_statements\"` " +
                        "pointing to the Digital Asset Links JSON file. See " +
                        "https://developer.android.com/identity/sign-in/credential-manager#add-support-dal",
                ),
            )
        }
    }

    override fun afterCheckRootProject(context: Context) {
        if (context.isGlobalAnalysis()) {
            checkPartialResults(context, context.getPartialResults(ISSUE))
        }
    }
}