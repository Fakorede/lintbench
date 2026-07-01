package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RegistrationDetector extends Detector implements Detector.UastScanner, Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
        "Registered",
        "Class is not registered in the manifest",
        "Activities, services and content providers should be registered in the " +
        "`AndroidManifest.xml` file using `<activity>`, `<service>` and `<provider>` tags.\n\n" +
        "If your activity is simply a parent class intended to be subclassed by other " +
        "\"real\" activities, make it an abstract class.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(
            RegistrationDetector.class,
            EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)
        )
    );

    private final Set<String> mRegistered = new HashSet<>();
    private final List<PendingReport> mPendingReports = new ArrayList<>();

    private static class PendingReport {
        final JavaContext context;
        final UClass uClass;
        final Location location;
        final String className;
        final String type;

        PendingReport(JavaContext context, UClass uClass, Location location, String className, String type) {
            this.context = context;
            this.uClass = uClass;
            this.location = location;
            this.className = className;
            this.type = type;
        }
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mRegistered.clear();
        mPendingReports.clear();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("activity", "service", "provider");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (name != null && !name.isEmpty()) {
            String pkg = getPackageName(context, element);
            String resolvedName = name;
            if (resolvedName.contains("${applicationId}")) {
                resolvedName = resolvedName.replace("${applicationId}", pkg);
            }
            if (!resolvedName.contains("${")) {
                String resolved = resolveClassName(resolvedName, pkg);
                mRegistered.add(resolved.replace('$', '.'));
            }
        }
    }

    private String getPackageName(XmlContext context, Element element) {
        String pkg = context.getProject().getPackage();
        if (pkg != null && !pkg.isEmpty()) {
            return pkg;
        }
        Element root = element.getOwnerDocument().getDocumentElement();
        if (root != null) {
            pkg = root.getAttribute("package");
            if (pkg != null) {
                return pkg;
            }
        }
        return "";
    }

    private String resolveClassName(String name, String pkg) {
        if (name.startsWith(".")) {
            return pkg.isEmpty() ? name.substring(1) : pkg + name;
        } else if (!name.contains(".")) {
            return pkg.isEmpty() ? name : pkg + "." + name;
        } else {
            return name;
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
            "android.app.Activity",
            "android.app.Service",
            "android.content.ContentProvider"
        );
    }

    private String getType(UClass uClass, JavaContext context) {
        if (context.getEvaluator().extendsClass(uClass, "android.app.Activity", false)) {
            return "activity";
        } else if (context.getEvaluator().extendsClass(uClass, "android.app.Service", false)) {
            return "service";
        } else if (context.getEvaluator().extendsClass(uClass, "android.content.ContentProvider", false)) {
            return "provider";
        }
        return null;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (context.isTestSource()) {
            return;
        }
        if (context.getEvaluator().isAbstract(declaration)) {
            return;
        }
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null || qualifiedName.isEmpty()) {
            return;
        }
        String type = getType(declaration, context);
        if (type == null) {
            return;
        }
        mPendingReports.add(new PendingReport(context, declaration, context.getNameLocation(declaration), qualifiedName, type));
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (PendingReport report : mPendingReports) {
            String className = report.className;
            if (!mRegistered.contains(className)) {
                report.context.report(
                    ISSUE,
                    report.uClass,
                    report.location,
                    "The <" + report.type + "> " + className + " is not registered in the manifest"
                );
            }
        }
        mRegistered.clear();
        mPendingReports.clear();
    }
}