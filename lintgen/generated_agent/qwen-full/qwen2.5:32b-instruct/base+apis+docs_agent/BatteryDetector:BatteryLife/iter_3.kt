package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

class BatteryDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "BatteryLifeIssues",
            briefDescription = "This issue flags code that either negatively affects battery life or uses APIs that have recently changed behavior to prevent background tasks from consuming memory and battery excessively.",
            explanation = """
                Generally, you should be using `WorkManager` instead. For more details on how to update your code, please see https://developer.android.android.com/topic/performance/background-optimization
            """,
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(BatteryDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf(
            "startService",
            "bindService",
            "registerReceiver"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        context.report(ISSUE, node, context.getLocation(node), "This method call can negatively affect battery life. Consider using WorkManager instead.")
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("service", "receiver")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        if (getApplicableElements()?.contains(tagName) == true) {
            context.report(ISSUE, element, context.getLocation(element), "This XML element can negatively affect battery life. Consider using WorkManager instead.")
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.MANIFEST
    }
}