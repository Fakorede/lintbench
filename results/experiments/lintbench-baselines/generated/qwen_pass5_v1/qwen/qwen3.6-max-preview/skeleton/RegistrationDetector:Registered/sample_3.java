package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;

public class RegistrationDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(RegistrationDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "Registered",
                    "Class is not registered in the manifest",
                    "Activities, services and content providers should be registered in the AndroidManifest.xml file using <activity>, <service> and <provider> tags.\n\n"
                    + "If your activity is simply a parent class intended to be subclassed by other \"real\" activities, make it an abstract class.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.app.Service",
                "android.content.ContentProvider",
                "android.content.BroadcastReceiver",
                "android.app.Application"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isAbstract()) {
            return;
        }
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        if (!isRegisteredInManifest(context, qualifiedName)) {
            context.report(ISSUE, declaration, context.getLocation(declaration),
                    "Class is not registered in the manifest");
        }
    }

    private boolean isRegisteredInManifest(@NonNull JavaContext context, @NonNull String className) {
        File manifest = context.getProject().getManifest();
        if (manifest == null || !manifest.exists()) {
            return true;
        }
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        try (BufferedReader reader = new BufferedReader(new FileReader(manifest))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("android:name=\"" + className + "\"")
                        || line.contains("android:name=\"." + simpleName + "\"")
                        || line.contains("android:name=\"" + simpleName + "\"")) {
                    return true;
                }
            }
        } catch (IOException e) {
            return true;
        }
        return false;
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        // No cross-file aggregation required for this detector.
        // Issues are reported directly during visitClass execution.
    }
}