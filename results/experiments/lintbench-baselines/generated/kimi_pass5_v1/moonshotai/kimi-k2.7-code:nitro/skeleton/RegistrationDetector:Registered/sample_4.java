package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UClass;

public class RegistrationDetector extends LayoutDetector {

    private static final String CLASS_ACTIVITY = "android.app.Activity";
    private static final String CLASS_SERVICE = "android.app.Service";
    private static final String CLASS_PROVIDER = "android.content.ContentProvider";

    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_PROVIDER = "provider";
    private static final String ATTR_NAME = "android:name";

    private static final String KEY_CANDIDATE_PREFIX = "candidate:";
    private static final String KEY_REGISTERED_PREFIX = "registered:";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RegistrationDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST_SCOPE));

    public static final Issue ISSUE =
            Issue.create(
                    "Registered",
                    "Class is not registered in the manifest",
                    "Activities, services and content providers should be registered in the "
                            + "AndroidManifest.xml file using <activity>, <service> and "
                            + "<provider> tags. If this class is only intended to be subclassed "
                            + "by other components, make it abstract.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(CLASS_ACTIVITY, CLASS_SERVICE, CLASS_PROVIDER);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        PsiClass psiClass = (PsiClass) declaration.getJavaPsi();
        if (psiClass == null
                || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)
                || psiClass.isInterface()) {
            return;
        }

        String fqcn = psiClass.getQualifiedName();
        if (fqcn == null) {
            return;
        }

        PartialResult partialResult = context.getPartialResult(ISSUE);
        if (partialResult == null) {
            return;
        }

        Incident incident =
                new Incident(ISSUE, context.getLocation(declaration), fqcn);
        partialResult.put(
                KEY_CANDIDATE_PREFIX + fqcn + "@" + context.file.getPath(),
                incident);
    }

    @Override
    public List<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_SERVICE, TAG_PROVIDER);
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        Project project = context.getMainProject();
        String packageName = project.getPackage();
        String fqcn = resolveFqcn(name, packageName);
        if (fqcn == null) {
            return;
        }

        PartialResult partialResult = context.getPartialResult(ISSUE);
        if (partialResult == null) {
            return;
        }

        partialResult.put(KEY_REGISTERED_PREFIX + fqcn, fqcn);
    }

    @Override
    public void checkPartialResults(Context context, PartialResult partialResults) {
        Map<String, ?> data = partialResults.getAllData();
        if (data == null || data.isEmpty()) {
            return;
        }

        Set<String> registered = new HashSet<>();
        for (Map.Entry<String, ?> entry : data.entrySet()) {
            if (entry.getKey().startsWith(KEY_REGISTERED_PREFIX)) {
                Object value = entry.getValue();
                if (value instanceof String) {
                    registered.add((String) value);
                }
            }
        }

        for (Map.Entry<String, ?> entry : data.entrySet()) {
            if (entry.getKey().startsWith(KEY_CANDIDATE_PREFIX)) {
                Object value = entry.getValue();
                if (value instanceof Incident) {
                    Incident incident = (Incident) value;
                    String fqcn = incident.getMessage();
                    if (!registered.contains(fqcn)) {
                        context.report(
                                ISSUE,
                                incident.getLocation(),
                                fqcn + " is not registered in the manifest");
                    }
                }
            }
        }
    }

    private static String resolveFqcn(String name, String packageName) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (name.startsWith(".")) {
            return packageName + name;
        }
        if (name.indexOf('.') == -1) {
            return packageName + "." + name;
        }
        return name;
    }
}