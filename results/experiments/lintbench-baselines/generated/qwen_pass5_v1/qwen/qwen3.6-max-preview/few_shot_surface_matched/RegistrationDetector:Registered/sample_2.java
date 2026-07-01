package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RegistrationDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "Registered",
                    "Class is not registered in the manifest",
                    "Activities, services and content providers should be registered in the "
                            + "`AndroidManifest.xml` file using `<activity>`, `<service>` and "
                            + "`<provider>` tags.\n\n"
                            + "If your activity is simply a parent class intended to be subclassed "
                            + "by other \"real\" activities, make it an abstract class.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            RegistrationDetector.class,
                            Scope.JAVA_FILE_SCOPE,
                            Scope.MANIFEST_SCOPE));

    private final Set<String> mComponents = new HashSet<>();
    private final Set<String> mRegistered = new HashSet<>();
    private final Map<String, Location> mLocations = new HashMap<>();

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.app.Service",
                "android.content.ContentProvider",
                "android.content.BroadcastReceiver",
                "android.app.Application");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isAbstract() || declaration.isInterface()) {
            return;
        }
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            mComponents.add(qualifiedName);
            mLocations.put(qualifiedName, context.getNameLocation(declaration));
        }
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("activity", "service", "provider", "receiver", "application");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("android:name");
        if (name != null && !name.isEmpty()) {
            String pkg = context.getProject().getPackageName();
            if (name.startsWith(".")) {
                if (pkg != null) {
                    name = pkg + name;
                }
            } else if (!name.contains(".")) {
                if (pkg != null) {
                    name = pkg + "." + name;
                }
            }
            mRegistered.add(name);
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (String component : mComponents) {
            if (!mRegistered.contains(component)) {
                Location location = mLocations.get(component);
                if (location != null) {
                    context.report(ISSUE, location, "Class is not registered in the manifest");
                }
            }
        }
    }
}