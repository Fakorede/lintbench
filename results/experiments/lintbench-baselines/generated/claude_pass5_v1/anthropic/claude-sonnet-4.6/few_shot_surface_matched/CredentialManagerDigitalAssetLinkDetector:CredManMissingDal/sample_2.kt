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
        private const val KEY_USES_PASSWORD_CREDENTIAL = "usesPasswordCredential"
        private const val KEY_LOCATION = "location"

        private const val GET_CREDENTIAL_REQUEST_CLASS =
            "androidx.credentials.GetCredentialRequest"
        private const val PASSWORD_CREDENTIAL_OPTION_CLASS =
            "androidx.credentials.GetPasswordOption"
        private const val CREATE_PASSWORD_REQUEST_CLASS =
            "androidx.credentials.CreatePasswordRequest"

        private const val META_DATA_ASSET_STATEMENTS =
            "asset_statements"

        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements \
                string resource file that includes the `assetlinks.json` files to load must be \
                declared in the manifest using a `<meta-data>` element.

                Without this, the Credential Manager will not be able to associate your app \
                with your website, and password sign-in may not work correctly.

                See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal \
                for details on how to add the required Digital Asset Link configuration.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true,
        )

        private val CREDENTIAL_CONSTRUCTOR_TYPES = listOf(
            PASSWORD_CREDENTIAL_OPTION_CLASS,
            CREATE_PASSWORD_REQUEST_CLASS,
        )
    }

    override fun getApplicableConstructorTypes(): List<String> = CREDENTIAL_CONSTRUCTOR_TYPES

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        val qualifiedName = constructor.containingClass?.qualifiedName ?: return
        if (qualifiedName !in CREDENTIAL_CONSTRUCTOR_TYPES) return

        val partialResults = context.getPartialResults(ISSUE)
        val map = partialResults.map()
        if (!map.getBoolean(KEY_USES_PASSWORD_CREDENTIAL, false)) {
            map.put(KEY_USES_PASSWORD_CREDENTIAL, true)
            val location = context.getLocation(node)
            map.put(KEY_LOCATION, location)
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        if (!partialResults.map().getBoolean(KEY_USES_PASSWORD_CREDENTIAL, false)) return

        val mainMap = context.getPartialResults(ISSUE).map()
        if (!mainMap.getBoolean(KEY_USES_PASSWORD_CREDENTIAL, false)) {
            mainMap.put(KEY_USES_PASSWORD_CREDENTIAL, true)
            val location = partialResults.map().getLocation(KEY_LOCATION)
            if (location != null) {
                mainMap.put(KEY_LOCATION, location)
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val partialResults = context.getPartialResults(ISSUE).map()
        val usesPasswordCredential = partialResults.getBoolean(KEY_USES_PASSWORD_CREDENTIAL, false)
        if (!usesPasswordCredential) return

        val hasAssetStatements = hasAssetStatementsMetaData(context)
        if (hasAssetStatements) return

        val location = partialResults.getLocation(KEY_LOCATION) ?: Location.create(context.project.dir)

        context.report(
            Incident(
                ISSUE,
                location,
                "This app uses Credential Manager for password sign-in, but is missing a " +
                    "`<meta-data>` element in the manifest declaring the Digital Asset Link " +
                    "asset statements resource (e.g. `asset_statements`). " +
                    "See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal"
            )
        )
    }

    private fun hasAssetStatementsMetaData(context: Context): Boolean {
        val mainProject = context.project
        val manifestFiles = mainProject.manifestFiles
        for (manifestFile in manifestFiles) {
            val document = manifestFile.document ?: continue
            val applicationNodes = document.getElementsByTagName(SdkConstants.TAG_APPLICATION)
            for (i in 0 until applicationNodes.length) {
                val applicationNode = applicationNodes.item(i)
                val children = applicationNode.childNodes
                for (j in 0 until children.length) {
                    val child = children.item(j)
                    if (child.nodeName == SdkConstants.TAG_META_DATA) {
                        val nameAttr = child.attributes?.getNamedItem(
                            SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_NAME
                        )
                        val valueAttr = child.attributes?.getNamedItem(
                            SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_RESOURCE
                        ) ?: child.attributes?.getNamedItem(
                            SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_VALUE
                        )
                        val nameValue = nameAttr?.nodeValue ?: continue
                        if (nameValue.contains(META_DATA_ASSET_STATEMENTS, ignoreCase = true) &&
                            valueAttr != null
                        ) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }
}