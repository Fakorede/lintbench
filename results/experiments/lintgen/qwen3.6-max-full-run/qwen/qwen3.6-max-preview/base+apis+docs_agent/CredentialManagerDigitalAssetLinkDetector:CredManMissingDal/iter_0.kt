package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private val KEY_CREDMAN_USED = Key.create<Boolean>("CredManUsed")
        private val KEY_DAL_PRESENT = Key.create<Boolean>("DalPresent")
        private val KEY_MANIFEST_LOCATION = Key.create<Location>("ManifestLocation")

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
                EnumSet.of(Scope.ALL_JAVA_FILES, Scope.MANIFEST)
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
        context.project.putUserData(KEY_CREDMAN_USED, true)
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(SdkConstants.TAG_META_DATA)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.file.name != SdkConstants.ANDROID_MANIFEST_XML) return

        if (context.project.getUserData(KEY_MANIFEST_LOCATION) == null) {
            context.project.putUserData(KEY_MANIFEST_LOCATION, context.getLocation(element))
        }

        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        if (name == "asset_statements") {
            context.project.putUserData(KEY_DAL_PRESENT, true)
        }
    }

    override fun afterCheckEachProject(context: Context) {
        val project = context.project
        val credManUsed = project.getUserData(KEY_CREDMAN_USED) == true
        val dalPresent = project.getUserData(KEY_DAL_PRESENT) == true

        if (credManUsed && !dalPresent) {
            val location = project.getUserData(KEY_MANIFEST_LOCATION)
                ?: Location.create(project.manifest ?: return)
            context.report(
                ISSUE,
                location,
                "Missing Digital Asset Links meta-data. When using Credential Manager for password sign-in, " +
                "you must declare `<meta-data android:name=\"asset_statements\" android:resource=\"@string/asset_statements\" />` " +
                "in your AndroidManifest.xml."
            )
        }

        project.putUserData(KEY_CREDMAN_USED, null)
        project.putUserData(KEY_DAL_PRESENT, null)
        project.putUserData(KEY_MANIFEST_LOCATION, null)
    }
}