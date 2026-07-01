package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
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
import com.android.tools.lint.detector.api.XmlScannerConstants
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

class InternalInsetResourceDetector : Detector(), XmlScanner, SourceCodeScanner {

    companion object {
        private val INSET_DIMENS = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_width"
        )

        private val FRAMEWORK_DIMEN_PREFIXES = listOf(
            "@android:dimen/",
            "@*android:dimen/",
            "?android:dimen/",
            "?*android:dimen/"
        )

        @JvmField
        val ISSUE = Issue.create(
            "InternalInsetResource",
            "Using internal inset dimension resource",
            """
                The internal inset