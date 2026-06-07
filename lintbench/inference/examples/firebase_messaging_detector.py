SOURCE = '''
// EXAMPLE: FirebaseMessagingDetector (Java, SourceCodeScanner — class visitor)
// Issue: MissingFirebaseInstanceTokenRefresh
// Explanation: Apps using Firebase Cloud Messaging should implement
// FirebaseMessagingService#onNewToken() to observe token changes.

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
import org.jetbrains.uast.UClass;

public class FirebaseMessagingDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(FirebaseMessagingDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String FIREBASE_MESSAGING_SERVICE =
            "com.google.firebase.messaging.FirebaseMessagingService";

    public static final Issue MISSING_TOKEN_REFRESH =
            Issue.create(
                            "MissingFirebaseInstanceTokenRefresh",
                            "Missing Firebase Messaging Callback",
                            "Apps that use Firebase Cloud Messaging should implement the "
                                    + "`FirebaseMessagingService#onNewToken()` callback in order to "
                                    + "observe token changes.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public FirebaseMessagingDetector() {}

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (FIREBASE_MESSAGING_SERVICE.equals(declaration.getQualifiedName())) {
            return;
        }
        for (PsiMethod method : declaration.getMethods()) {
            if (method.getName().equals("onNewToken")) {
                return;
            }
        }
        context.report(
                MISSING_TOKEN_REFRESH,
                declaration,
                context.getNameLocation(declaration),
                "Apps that use Firebase Cloud Messaging should implement "
                        + "`onNewToken()` in order to observe token changes");
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(FIREBASE_MESSAGING_SERVICE);
    }
}
'''.strip()
