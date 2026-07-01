package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.uast.UClass;

public class RegistrationDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RegistrationDetector.class,
                    java.util.EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE));

    public static final Issue ISSUE =
            Issue.create(
                    "Registered",
                    "Class is not registered in the manifest",
                    "Activities, services and content providers should be registered in the "
                            + "`AndroidManifest.xml` file using `<activity>`, `<service>` and "
                            + "`<provider>` tags.\n\n"
                            + "If your activity is simply a parent class intended to be "
                            + "subclassed by other \"real\" activities, make it an abstract class.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ANDROID_ACTIVITY = "android.app.Activity";
    private static final String ANDROID_SERVICE = "android.app.Service";
    private static final String ANDROID_PROVIDER = "android.content.ContentProvider";
    private static final String ANDROID_RECEIVER = "android.content.BroadcastReceiver";

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList("activity", "service", "provider", "receiver");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String packageName = context.getMainProject().getPackage();
        if (packageName == null) {
            return;
        }

        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name == null) {
            return;
        }

        String fqcn = resolveClassName(packageName, name.trim());
        if (fqcn == null) {
            return;
        }

        java.util.Map<String, Location> map = getMap(context.getPartialResults(ISSUE));
        map.remove(fqcn);
    }

    @Override
    public java.util.List<String> applicableSuperClasses() {
        return java.util.Arrays.asList(
                ANDROID_ACTIVITY, ANDROID_SERVICE, ANDROID_PROVIDER, ANDROID_RECEIVER);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.isAbstract()) {
            return;
        }

        String name = declaration.getQualifiedName();
        if (name == null) {
            return;
        }

        java.util.Map<String, Location> map = getMap(context.getPartialResults(ISSUE));
        map.put(name, context.getNameLocation(declaration));
    }

    @Override
    public void checkPartialResults(Context context, PartialResult result) {
        java.util.Map<String, Location> map = getMap(result);
        for (String className : map.keySet()) {
            Location location = map.get(className);
            String message =
                    String.format("Class %1$s is not registered in the manifest", className);
            context.report(ISSUE, location, message);
        }
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Location> getMap(PartialResult result) {
        return (java.util.Map<String, Location>) (java.util.Map<?, ?>) result.getMap();
    }

    private static String resolveClassName(String packageName, String className) {
        if (className == null || className.isEmpty()) {
            return null;
        }
        if (className.startsWith(".")) {
            return packageName + className;
        }
        if (className.contains(".")) {
            return className;
        }
        return packageName + "." + className;
    }
}