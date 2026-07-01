package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiModifier;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RegistrationDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String CLASS_ACTIVITY = "android.app.Activity";
    private static final String CLASS_SERVICE = "android.app.Service";
    private static final String CLASS_PROVIDER = "android.content.ContentProvider";

    public static final Issue ISSUE =
            Issue.create(
                    "Registered",
                    "Class is not registered in the manifest",
                    "Activities, services and content providers should be registered in the "
                            + "`AndroidManifest.xml` file using `<activity>`, `<service>` and "
                            + "`<provider>` tags. If your activity is simply a parent class "
                            + "intended to be subclassed by other \"real\" activities, make it "
                            + "an abstract class.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            RegistrationDetector.class,
                            Scope.MANIFEST_AND_JAVA_SCOPE
                    )
            ).setAndroidSpecific(true);

    public RegistrationDetector() {}

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(CLASS_ACTIVITY, CLASS_SERVICE, CLASS_PROVIDER);
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
        qualifiedName = qualifiedName.replace('$', '.');

        PartialResult partialResult = context.getPartialResults(ISSUE);
        LintMap map = partialResult.map();

        int count = map.getInt("count", 0);
        map.put("class_name_" + count, qualifiedName);
        map.put("class_file_" + count, context.file.getPath());
        map.put("count", count + 1);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("activity", "activity-alias", "service", "provider");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if (name == null || name.isEmpty()) {
            name = element.getAttribute("android:name");
        }
        if (name != null && !name.isEmpty()) {
            Document doc = element.getOwnerDocument();
            String pkg = doc.getDocumentElement().getAttribute("package");
            String resolved = resolveClassName(name, pkg);
            if (resolved != null) {
                resolved = resolved.replace('$', '.');
                PartialResult partialResult = context.getPartialResults(ISSUE);
                LintMap map = partialResult.map();
                int regCount = map.getInt("reg_count", 0);
                map.put("reg_name_" + regCount, resolved);
                map.put("reg_count", regCount + 1);
            }
        }
    }

    private String resolveClassName(String className, String packageName) {
        if (className == null || className.isEmpty()) {
            return null;
        }
        if (className.startsWith(".")) {
            return packageName + className;
        } else if (!className.contains(".")) {
            return packageName + "." + className;
        }
        return className;
    }

    @Override
    public void checkPartialResults(Context context, PartialResult partialResult) {
        Set<String> registered = new HashSet<>();
        for (Project project : partialResult.getProjects()) {
            LintMap map = partialResult.getMap(project);
            if (map == null) continue;
            int regCount = map.getInt("reg_count", 0);
            for (int i = 0; i < regCount; i++) {
                String name = map.getString("reg_name_" + i);
                if (name != null) {
                    registered.add(name);
                }
            }
        }

        for (Project project : partialResult.getProjects()) {
            LintMap map = partialResult.getMap(project);
            if (map == null) continue;
            int count = map.getInt("count", 0);
            for (int i = 0; i < count; i++) {
                String className = map.getString("class_name_" + i);
                String filePath = map.getString("class_file_" + i);
                if (className != null && filePath != null) {
                    if (!registered.contains(className)) {
                        File file = new File(filePath);
                        Location location = Location.create(file);
                        context.report(
                                ISSUE,
                                location,
                                "Class " + className + " is not registered in the manifest"
                        );
                    }
                }
            }
        }
    }
}