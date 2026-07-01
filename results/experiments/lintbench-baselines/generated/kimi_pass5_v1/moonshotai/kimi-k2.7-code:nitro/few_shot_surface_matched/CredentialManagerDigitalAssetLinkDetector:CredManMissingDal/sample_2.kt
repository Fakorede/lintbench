package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val GET_PASSWORD_OPTION = "androidx.credentials.GetPasswordOption"
        private const val PARTIAL_RESULT_KEY = "getpasswordoption.used"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements
                string resource file that includes the `assetlinks.json` files to load must be
                declared in the manifest using a `<meta-data>` element.

                For more information, see the Credential Manager documentation.
            """.trimIndent(),
            moreInfo = "https://developer.android.com/identity/sign-in/credential-manager#add-support-dal",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true
        )
    }

    override fun getApplicableConstructorTypes(): List<String> = listOf(GET_PASSWORD_OPTION)

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        if (!context.evaluator.isMemberInClass(constructor, GET_PASSWORD_OPTION)) {
            return
        }
        context.partialResults.map[PARTIAL_RESULT_KEY] = true
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        val found = partialResults.map[PARTIAL_RESULT_KEY] == true ||
                partialResults.maps.values.any { it[PARTIAL_RESULT_KEY] == true }
        partialResults.map[PARTIAL_RESULT_KEY] = found
    }

    override fun afterCheckRootProject(context: Context) {
        if (context.partialResults.map[PARTIAL_RESULT_KEY] != true) {
            return
        }

        val rootTag = context.mainProject.manifest?.document?.rootTag ?: return
        val application = rootTag.findSubTag("application")

        application?.findSubTags("meta-data")?.forEach { meta ->
            val name = meta.getAttributeValue("name", ANDROID_URI)
            val resource = meta.getAttributeValue("resource", ANDROID_URI)
            if (name == "asset_statements" && resource?.startsWith("@string/") == true) {
                return
            }
        }

        val location = context.getLocation(application ?: rootTag)
        val message = "Missing Digital Asset Link for Credential Manager password sign-in. " +
                "Add `<meta-data android:name=\"asset_statements\" " +
                "android:resource=\"@string/asset_statements\" />` to the `<application>` element."
        context.report(Incident(ISSUE, location, message))
    }
}