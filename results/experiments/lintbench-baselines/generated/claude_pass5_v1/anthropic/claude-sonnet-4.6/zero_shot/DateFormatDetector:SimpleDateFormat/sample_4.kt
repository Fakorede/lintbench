/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * Checks for the use of [java.text.SimpleDateFormat] without an explicit locale.
 */
class DateFormatDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            DateFormatDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val DATE_FORMAT = Issue.create(
            id = "SimpleDateFormat",
            briefDescription = "Implied locale in date format",
            explanation = """
                Almost all callers should use `getDateInstance()`, `getDateTimeInstance()`, \
                or `getTimeInstance()` to get a ready-made instance of SimpleDateFormat \
                suitable for the user's locale. The main reason you'd create an instance \
                this class directly is because you need to format/parse a specific \
                machine-readable format, in which case you almost certainly want to \
                explicitly ask for US to ensure that you get ASCII digits (rather than, \
                say, Arabic digits).

                Therefore, you should either use the form of the SimpleDateFormat \
                constructor where you pass in an explicit locale, such as Locale.US, or \
                use one of the get instance methods, or suppress this error if really know \
                what you are doing.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            moreInfo = "https://developer.android.com/reference/java/text/SimpleDateFormat.html",
            implementation = IMPLEMENTATION
        )

        private const val SIMPLE_DATE_FORMAT_CLASS = "java.text.SimpleDateFormat"
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(SIMPLE_DATE_FORMAT_CLASS)
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        // If the constructor has no arguments, it uses the default locale — flag it.
        // If the constructor has arguments, check whether a Locale is among them.
        // The constructors are:
        //   SimpleDateFormat()
        //   SimpleDateFormat(String pattern)
        //   SimpleDateFormat(String pattern, DateFormatSymbols formatSymbols)
        //   SimpleDateFormat(String pattern, Locale locale)   <-- acceptable
        //
        // We consider the call acceptable only when a java.util.Locale argument is passed.

        val parameterTypes = constructor.parameterList.parameters
        val hasLocaleParameter = parameterTypes.any { param ->
            param.type.canonicalText == "java.util.Locale"
        }

        if (!hasLocaleParameter) {
            context.report(
                DATE_FORMAT,
                node,
                context.getLocation(node),
                "To get local-independent date formatting, use `SimpleDateFormat(String pattern, Locale locale)`, e.g. `Locale.US`"
            )
        }
    }
}