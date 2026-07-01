package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiModifier;
import org.jetbrains.uast.UClass;

public class RegistrationDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "Registered",
                    "Class is not registered in the manifest",
                    "Activities, services and content providers should be registered in the "
                            + "AndroidManifest.xml file using <activity>, <service> and "
                            + "<provider> tags.\n\nIf your activity is simply a parent class "
                            + "intended to be subclassed by other \"real\" activities, make it "
                            + "an abstract class.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            RegistrationDetector.class,
                            Scope.JAVA_FILE_SCOPE,
                            Scope.MANIFEST_SCOPE));

    private static final String ANDROID_APP_ACTIVITY = "android.app.Activity";
    private static final String ANDROID_APP_SERVICE = "android.app.Service";
    private static final String ANDROID_CONTENT_CONTENT_PROVIDER =
            "android.content.ContentProvider";

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public RegistrationDetector() {}

    @Override
    public java.util.List<String> applicableSuperClasses() {
        return java.util.Arrays.asList(
                ANDROID_APP_ACTIVITY, ANDROID_APP_SERVICE, ANDROID_CONTENT_CONTENT_PROVIDER);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        context.getPartialResults(ISSUE)
                .map()
                .put(qualifiedName, context.getNameLocation(declaration));
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList("activity", "activity-alias", "service", "provider");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String fqcn = getFqcn(context, element);
        if (fqcn != null) {
            context.getPartialResults(ISSUE).map().put(fqcn, Boolean.TRUE);
        }
    }

    private static String getFqcn(XmlContext context, org.w3c.dom.Element element) {
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name == null || name.isEmpty()) {
            return null;
        }
        String pkg = context.getMainProject().getPackage();
        if (name.startsWith(".")) {
            if (pkg != null) {
                name = pkg + name;
            }
        } else if (!name.contains(".")) {
            if (pkg != null) {
                name = pkg + "." + name;
            }
        }
        return name;
    }

    @Override
    public void checkPartialResults(Context context, Project project) {
        java.util.Map<String, Object> map = context.getPartialResults(ISSUE).map();
        for (java.util.Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Location) {
                context.report(
                        ISSUE,
                        (Location) value,
                        "Class is not registered in the manifest");
            }
        }
    }
}