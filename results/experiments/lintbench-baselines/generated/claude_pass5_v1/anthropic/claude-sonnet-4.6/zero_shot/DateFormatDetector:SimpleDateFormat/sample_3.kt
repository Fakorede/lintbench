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
 * Detector for SimpleDateFormat usage without an explicit locale.
 *
 * Flags calls to `new SimpleDateFormat(String)` or `new SimpleDateFormat(String, DateFormatSymbols)`
 * that do not pass an explicit `Locale`, as this can lead to unexpected behavior on devices with
 * non-US locales.
 */
class DateFormatDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val SIMPLE_DATE_FORMAT_CLASS = "java.text.SimpleDateFormat"

        /** The main issue reported by this detector. */
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
                constructor where you pass in an explicit locale, such as `Locale.US`, or \
                use one of the `getInstance` methods, or suppress this error if you really \
                know what you are doing.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            moreInfo = "https://developer.android.com/reference/java/text/SimpleDateFormat.html",
            implementation = Implementation(
                DateFormatDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        /**
         * Returns true if the given constructor call is a problematic SimpleDateFormat
         * construction (i.e., missing an explicit Locale argument).
         *
         * The constructors of SimpleDateFormat are:
         *   SimpleDateFormat()                                    -- no args, locale-sensitive
         *   SimpleDateFormat(String pattern)                      -- locale-sensitive, flagged
         *   SimpleDateFormat(String pattern, DateFormatSymbols)   -- locale-sensitive, flagged
         *   SimpleDateFormat(String pattern, Locale)              -- explicit locale, OK
         */
        private fun isLocaleSpecific(call: UCallExpression): Boolean {
            val valueArguments = call.valueArguments
            // No-argument constructor: SimpleDateFormat() — also locale-sensitive but
            // the spec focuses on the string-pattern constructors; however we flag it too.
            // One-argument constructor: SimpleDateFormat(String) — flagged.
            // Two-argument constructor: check second arg type.
            if (valueArguments.isEmpty()) {
                // SimpleDateFormat() — uses default locale implicitly
                return true
            }
            if (valueArguments.size == 1) {
                // SimpleDateFormat(String pattern) — uses default locale
                return true
            }
            if (valueArguments.size == 2) {
                // SimpleDateFormat(String, Locale) is OK
                // SimpleDateFormat(String, DateFormatSymbols) is locale-sensitive
                val secondArgType = valueArguments[1].getExpressionType()
                val fqn = secondArgType?.canonicalText ?: return true
                // If the second argument is a Locale, it's fine
                if (fqn == "java.util.Locale") {
                    return false
                }
                // Otherwise (e.g., DateFormatSymbols) it's locale-sensitive
                return true
            }
            // Unknown / future constructor — assume OK
            return false
        }
    }

    // ---- Implements SourceCodeScanner ----

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(SIMPLE_DATE_FORMAT_CLASS)
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        if (isLocaleSpecific(node)) {
            val message = "To get local formatting use `getDateInstance()`, " +
                "`getDateTimeInstance()`, or `getTimeInstance()`, or use " +
                "`new SimpleDateFormat(String, Locale)` with for example " +
                "`Locale.US`; see `java.text.SimpleDateFormat#SimpleDateFormat(String)` " +
                "for details"
            context.report(DATE_FORMAT, node, context.getLocation(node), message)
        }
    }
}