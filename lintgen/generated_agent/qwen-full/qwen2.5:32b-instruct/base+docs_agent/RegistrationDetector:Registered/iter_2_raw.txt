package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.uast.UClass;

import java.util.Collections;
import java.util.List;

public class RegistrationDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "UnregisteredComponents",
            "Activities, services and content providers should be registered in the AndroidManifest.xml file using <activity>, <service> and <provider> tags.",
            "If your activity is simply a parent class intended to be subclassed by other \"real\" activities, make it an abstract class.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    RegistrationDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<String> getApplicableClasses() {
        return Collections.singletonList("android.app.Activity, android.app.Service, android.content.ContentProvider");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass klass) {
        if (klass.getModifierList().isAbstract()) {
            return;
        }
        String className = klass.getQualifiedName();
        Location location = context.getLocation(klass);
        if (!isComponentRegistered(context, className)) {
            context.report(ISSUE, klass, location, "Class is not registered in the manifest");
        }
    }

    private boolean isComponentRegistered(@NonNull JavaContext context, @NonNull String className) {
        return context.getManifest().getActivities().contains(className)
                || context.getManifest().getServices().contains(className)
                || context.getManifest().getContentProviders().contains(className);
    }
}