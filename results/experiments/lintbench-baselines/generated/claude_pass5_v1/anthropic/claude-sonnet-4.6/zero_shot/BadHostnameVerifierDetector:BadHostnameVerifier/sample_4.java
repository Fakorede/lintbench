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

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;

import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;

import java.util.Collections;
import java.util.List;

/**
 * Detector for insecure HostnameVerifier implementations that always return true.
 */
public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for implementations of `HostnameVerifier` whose `verify` method " +
            "always returns true (thus trusting any hostname) which could result in insecure " +
            "network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates " +
            "presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    BadHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE))
            .addMoreInfo("https://goo.gle/BadHostnameVerifier");

    private static final String HOSTNAME_VERIFIER_CLASS = "javax.net.ssl.HostnameVerifier";
    private static final String VERIFY_METHOD = "verify";

    public BadHostnameVerifierDetector() {
    }

    @Override
    public List<Class<? extends UElementHandler>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                // Check if this class implements HostnameVerifier
                if (!implementsHostnameVerifier(context, node)) {
                    return;
                }

                // Find the verify method
                for (UMethod method : node.getMethods()) {
                    if (isVerifyMethod(method)) {
                        checkVerifyMethod(context, method);
                    }
                }
            }
        };
    }

    private boolean implementsHostnameVerifier(JavaContext context, UClass node) {
        // Check direct interfaces
        for (String interfaceName : node.getInterfaces()) {
            // This approach checks the resolved types
        }

        // Use the evaluator to check if the class implements HostnameVerifier
        return context.getEvaluator().implementsInterface(node, HOSTNAME_VERIFIER_CLASS, false);
    }

    private boolean isVerifyMethod(UMethod method) {
        if (!VERIFY_METHOD.equals(method.getName())) {
            return false;
        }

        // Check that it has the right parameters: (String, SSLSession)
        int paramCount = method.getUastParameters().size();
        if (paramCount != 2) {
            return false;
        }

        return true;
    }

    private void checkVerifyMethod(JavaContext context, UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return;
        }

        // Check if the method always returns true
        if (alwaysReturnsTrue(body)) {
            context.report(
                    ISSUE,
                    method,
                    context.getLocation(method),
                    "`verify` always returns `true`, which could cause insecure network traffic " +
                    "due to trusting arbitrary hostnames in TLS/SSL certificates presented by peers");
        }
    }

    /**
     * Checks if an expression (method body) always returns true.
     */
    private boolean alwaysReturnsTrue(UExpression body) {
        if (body instanceof UBlockExpression) {
            UBlockExpression block = (UBlockExpression) body;
            List<UExpression> expressions = block.getExpressions();

            // Look for a single return true statement, or a block that only returns true
            int returnTrueCount = 0;
            int returnFalseCount = 0;
            int otherStatementCount = 0;

            for (UExpression expression : expressions) {
                if (expression instanceof UReturnExpression) {
                    UReturnExpression returnExpr = (UReturnExpression) expression;
                    UExpression returnValue = returnExpr.getReturnExpression();
                    if (isTrueLiteral(returnValue)) {
                        returnTrueCount++;
                    } else if (isFalseLiteral(returnValue)) {
                        returnFalseCount++;
                    } else {
                        // Return with non-literal expression
                        otherStatementCount++;
                    }
                } else {
                    otherStatementCount++;
                }
            }

            // If the only return statement is "return true" and there are no other
            // complex statements that could affect the result
            if (returnTrueCount > 0 && returnFalseCount == 0 && otherStatementCount == 0) {
                return true;
            }
        } else if (body instanceof UReturnExpression) {
            // Lambda-style body
            UReturnExpression returnExpr = (UReturnExpression) body;
            return isTrueLiteral(returnExpr.getReturnExpression());
        } else if (isTrueLiteral(body)) {
            // Direct expression body (e.g., lambda)
            return true;
        }

        return false;
    }

    private boolean isTrueLiteral(UExpression expression) {
        if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            return Boolean.TRUE.equals(value);
        }
        return false;
    }

    private boolean isFalseLiteral(UExpression expression) {
        if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            return Boolean.FALSE.equals(value);
        }
        return false;
    }
}