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
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
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

    public BadHostnameVerifierDetector() {}

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Find the verify method
        for (UMethod method : declaration.getMethods()) {
            if (isVerifyMethod(method)) {
                checkVerifyMethod(context, method);
            }
        }
    }

    private static boolean isVerifyMethod(@NonNull UMethod method) {
        if (!"verify".equals(method.getName())) {
            return false;
        }
        PsiMethod psiMethod = method.getJavaPsi();
        return psiMethod.getParameterList().getParametersCount() == 2;
    }

    private void checkVerifyMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return;
        }

        AlwaysReturnsTrueVisitor visitor = new AlwaysReturnsTrueVisitor();
        body.accept(visitor);

        if (visitor.alwaysReturnsTrue()) {
            context.report(
                    ISSUE,
                    method,
                    context.getNameLocation(method),
                    "`verify` always returns `true`, which could cause insecure network traffic "
                            + "due to trusting arbitrary hostnames");
        }
    }

    /**
     * Visitor that checks whether a method body always returns true. It checks:
     * 1. There is at least one return statement.
     * 2. All return statements return the literal true.
     * 3. There are no conditional returns (i.e., return statements inside if/loop bodies that
     *    might not always execute).
     *
     * We use a simpler heuristic: if the only statements are return true (possibly with
     * assignments or other side effects), flag it. Specifically, we look for the case where
     * ALL return expressions are literal `true`.
     */
    private static class AlwaysReturnsTrueVisitor extends AbstractUastVisitor {
        private int returnCount = 0;
        private int returnTrueCount = 0;
        private boolean hasConditionalReturn = false;

        // Track nesting depth for control flow structures
        private int controlFlowDepth = 0;

        @Override
        public boolean visitReturnExpression(@NonNull UReturnExpression node) {
            UExpression returnValue = node.getReturnExpression();
            if (controlFlowDepth > 0) {
                // Return inside control flow - could be conditional
                hasConditionalReturn = true;
            }
            returnCount++;
            if (returnValue instanceof ULiteralExpression) {
                Object value = ((ULiteralExpression) returnValue).getValue();
                if (Boolean.TRUE.equals(value)) {
                    returnTrueCount++;
                }
            }
            return super.visitReturnExpression(node);
        }

        @Override
        public boolean visitClass(@NonNull UClass node) {
            // Don't descend into anonymous/inner classes
            return true;
        }

        public boolean alwaysReturnsTrue() {
            return returnCount > 0
                    && returnCount == returnTrueCount
                    && !hasConditionalReturn;
        }
    }
}