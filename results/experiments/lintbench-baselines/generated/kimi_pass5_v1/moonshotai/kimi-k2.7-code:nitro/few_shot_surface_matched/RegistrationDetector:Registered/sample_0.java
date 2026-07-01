package com.android.tools.lint.checks;

import com.android.tools.lint.checks.LayoutDetector;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UClass;

public class RegistrationDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String CLASS_ACTIVITY = "android.app.Activity";
    private static final String CLASS_SERVICE = "android.app.Service";
    private static final String CLASS_PROVIDER = "android.content.ContentProvider";
    private static final String KEY_CLASS_NAMES = "classNames";
    private static final String PREFIX_REGISTERED = "reg:";
    private static final String PREFIX_LOCATION = "loc:";

    public static final Issue ISSUE =
            Issue.create(
                    "Registered",
                    "Not Registered",
                    "Activities, services and content providers should be registered in the "
                            + "AndroidManifest.xml file using <activity>, <service> and "
                            + "<provider> tags.\n\n"
                            + "If your activity is simply a parent class intended to be subclassed "
                            + "by other \"real\" activities, make it an abstract class.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            RegistrationDetector.class,
                            Scope.JAVA_FILE_SCOPE,
                            Scope.MANIFEST_SCOPE))
                    .setAndroidSpecific(true);

    public RegistrationDetector() {}

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(CLASS_ACTIVITY, CLASS_SERVICE, CLASS_PROVIDER);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.isAbstract()) {
            return;
        }
        if (declaration.getContainingClass() != null) {
            return;
        }
        String fqcn = declaration.getQualifiedName();
        if (fqcn == null) {
            return;
        }

        PartialResult partial = context.getPartialResults(ISSUE);
        LintMap projectMap = partial.getProjectScope(context.getProject());

        String classNames = projectMap.getString(KEY_CLASS_NAMES, "");
        if (!classNames.isEmpty()) {
            classNames += ";";
        }
        classNames += fqcn;
        projectMap.put(KEY_CLASS_NAMES, classNames);
        projectMap.put(PREFIX_LOCATION + fqcn, context.getNameLocation(declaration));
    }

    @Override
    public List<String> getApplicableElements() {
        return Arrays.asList("activity", "service", "provider");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        String pkg = context.getDocument().getDocumentElement().getAttribute("package");
        String fqcn;
        if (name.startsWith(".")) {
            fqcn = pkg + name;
        } else if (name.indexOf('.') == -1 && pkg != null && !pkg.isEmpty()) {
            fqcn = pkg + "." + name;
        } else {
            fqcn = name;
        }

        PartialResult partial = context.getPartialResults(ISSUE);
        LintMap global = partial.getGlobalScope();
        global.put(PREFIX_REGISTERED + fqcn, true);
    }

    @Override
    public void checkPartialResults(Context context, PartialResult pendingResult) {
        LintMap global = pendingResult.getGlobalScope();
        LintDriver driver = context.getDriver();

        for (Project project : driver.getProjects()) {
            LintMap projectMap = pendingResult.getProjectScope(project);
            String classNames = projectMap.getString(KEY_CLASS_NAMES, "");
            if (classNames.isEmpty()) {
                continue;
            }

            for (String fqcn : classNames.split(";")) {
                if (global.getBoolean(PREFIX_REGISTERED + fqcn, false)) {
                    continue;
                }

                Location location = projectMap.getLocation(PREFIX_LOCATION + fqcn);
                context.report(ISSUE, location, fqcn + " is not registered in the manifest");
            }
        }
    }
}