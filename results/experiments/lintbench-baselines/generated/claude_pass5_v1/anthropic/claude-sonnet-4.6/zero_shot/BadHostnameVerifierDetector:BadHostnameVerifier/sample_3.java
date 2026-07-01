/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE =
            Issue.create(
                            "BadHostnameVerifier",
                            "Insecure HostnameVerifier",
                            "This check looks for implementations of `HostnameVerifier` whose `verify` method"
                                    + " always returns true (thus trusting any hostname) which could result in"
                                    + " insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL"
                                    + " certificates presented by peers.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            new Implementation(
                                    BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE))
                    .addMoreInfo("https://goo.gle/BadHostnameVerifier");

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String VERIFY_METHOD = "verify";

    public BadHostnameVerifierDetector() {}

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if (!method.getName().equals(VERIFY_METHOD)) {
                continue;
            }

            // Check that this is the verify(String, SSLSession) method
            PsiMethod psiMethod = method.getJavaPsi();
            if (psiMethod.getParameterList().getParametersCount() != 2) {
                continue;
            }

            // Visit the method body looking for a return true statement
            VerifyMethodVisitor visitor = new VerifyMethodVisitor(context, method);
            method.accept(visitor);
        }
    }

    private static class VerifyMethodVisitor extends AbstractUastVisitor {
        private final JavaContext mContext;
        private final UMethod mMethod;
        private boolean mAlwaysReturnsTrue;
        private boolean mHasReturnFalse;
        private boolean mHasConditionalReturn;

        VerifyMethodVisitor(JavaContext context, UMethod method) {
            mContext = context;
            mMethod = method;
        }

        @Override
        public boolean visitMethod(@NonNull UMethod node) {
            // Don't recurse into nested methods/lambdas
            if (node != mMethod) {
                return true; // skip children
            }
            return false;
        }

        @Override
        public boolean visitReturnExpression(@NonNull UReturnExpression node) {
            UExpression returnValue = node.getReturnExpression();
            if (returnValue instanceof ULiteralExpression) {
                ULiteralExpression literal = (ULiteralExpression) returnValue;
                Object value = literal.getValue();
                if (Boolean.TRUE.equals(value)) {
                    mAlwaysReturnsTrue = true;
                } else if (Boolean.FALSE.equals(value)) {
                    mHasReturnFalse = true;
                }
            } else if (returnValue != null) {
                mHasConditionalReturn = true;
            }
            return false;
        }

        public void checkAndReport() {
            if (mAlwaysReturnsTrue && !mHasReturnFalse && !mHasConditionalReturn) {
                mContext.report(
                        ISSUE,
                        mMethod,
                        mContext.getLocation(mMethod),
                        "`verify` always returns `true`, which could cause insecure network traffic "
                                + "due to trusting arbitrary hostnames in TLS/SSL certificates presented by peers");
            }
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration, boolean isAnnotationType) {
        visitClass(context, declaration);
    }

    // Override to ensure we actually check the method properly
    private void analyzeVerifyMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        ReturnValueAnalyzer analyzer = new ReturnValueAnalyzer();
        method.accept(analyzer);

        if (analyzer.alwaysReturnsTrue()) {
            context.report(
                    ISSUE,
                    method,
                    context.getLocation(method),
                    "`verify` always returns `true`, which could cause insecure network traffic "
                            + "due to trusting arbitrary hostnames in TLS/SSL certificates presented by peers");
        }
    }

    /** Analyzes whether a verify method always returns true. */
    private static class ReturnValueAnalyzer extends AbstractUastVisitor {
        private boolean mFoundReturnTrue = false;
        private boolean mFoundReturnFalse = false;
        private boolean mFoundConditionalReturn = false;
        private int mMethodDepth = 0;

        @Override
        public boolean visitMethod(@NonNull UMethod node) {
            mMethodDepth++;
            if (mMethodDepth > 1) {
                return true; // skip nested methods
            }
            return false;
        }

        @Override
        public void afterVisitMethod(@NonNull UMethod node) {
            mMethodDepth--;
        }

        @Override
        public boolean visitReturnExpression(@NonNull UReturnExpression node) {
            if (mMethodDepth > 1) {
                return false;
            }
            UExpression returnValue = node.getReturnExpression();
            if (returnValue instanceof ULiteralExpression) {
                ULiteralExpression literal = (ULiteralExpression) returnValue;
                Object value = literal.getValue();
                if (Boolean.TRUE.equals(value)) {
                    mFoundReturnTrue = true;
                } else if (Boolean.FALSE.equals(value)) {
                    mFoundReturnFalse = true;
                } else {
                    mFoundConditionalReturn = true;
                }
            } else if (returnValue != null) {
                mFoundConditionalReturn = true;
            }
            return false;
        }

        boolean alwaysReturnsTrue() {
            return mFoundReturnTrue && !mFoundReturnFalse && !mFoundConditionalReturn;
        }
    }
}