package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.DefaultPosition;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintList;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

public class RegistrationDetector extends LayoutDetector implements SourceCodeScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RegistrationDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE, Scope.MANIFEST));

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
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.app.Service",
                "android.content.ContentProvider",
                "android.content.BroadcastReceiver");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String fqcn = declaration.getQualifiedName();
        if (fqcn == null) {
            return;
        }

        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        if (declaration.hasModifierProperty(PsiModifier.PRIVATE)) {
            return;
        }

        PsiClass containingClass = declaration.getContainingClass();
        if (containingClass != null && !declaration.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }

        PartialResult partialResults = context.getPartialResults(ISSUE);
        LintMap map = partialResults.map();
        LintList declaredList = map.getList("declared");
        if (declaredList == null) {
            declaredList = LintList.create();
            map.put("declared", declaredList);
        }

        LintMap classMap = LintMap.create();
        classMap.put("fqcn", fqcn);
        Location location = context.getNameLocation(declaration);
        classMap.put("file", location.getFile().getPath());
        Position start = location.getStart();
        Position end = location.getEnd();
        if (start != null) {
            classMap.put("start", start.getOffset());
        }
        if (end != null) {
            classMap.put("end", end.getOffset());
        }
        declaredList.add(classMap);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("activity", "service", "provider", "receiver", "activity-alias");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name == null || name.isEmpty()) {
            return;
        }
        String pkg = context.getProject().getPackage();
        String fqcn = name;
        if (pkg != null && !pkg.isEmpty()) {
            if (name.startsWith(".")) {
                fqcn = pkg + name;
            } else if (name.indexOf('.') == -1) {
                fqcn = pkg + "." + name;
            }
        }

        PartialResult partialResults = context.getPartialResults(ISSUE);
        LintMap map = partialResults.map();
        LintList registeredList = map.getList("registered");
        if (registeredList == null) {
            registeredList = LintList.create();
            map.put("registered", registeredList);
        }
        registeredList.add(fqcn);

        if ("activity-alias".equals(element.getTagName())) {
            String target = element.getAttributeNS(ANDROID_URI, "targetActivity");
            if (target != null && !target.isEmpty()) {
                String targetFqcn = target;
                if (pkg != null && !pkg.isEmpty()) {
                    if (target.startsWith(".")) {
                        targetFqcn = pkg + target;
                    } else if (target.indexOf('.') == -1) {
                        targetFqcn = pkg + "." + target;
                    }
                }
                registeredList.add(targetFqcn);
            }
        }
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        Set<String> registered = new HashSet<>();
        List<DeclaredClass> declared = new ArrayList<>();

        for (Project project : partialResults.projects()) {
            LintMap map = partialResults.map(project);

            LintList registeredList = map.getList("registered");
            if (registeredList != null) {
                for (int i = 0; i < registeredList.size(); i++) {
                    registered.add(registeredList.getString(i));
                }
            }

            LintList declaredList = map.getList("declared");
            if (declaredList != null) {
                for (int i = 0; i < declaredList.size(); i++) {
                    LintMap classMap = declaredList.getMap(i);
                    if (classMap != null) {
                        String fqcn = classMap.getString("fqcn");
                        String filePath = classMap.getString("file");
                        Integer start = classMap.getInteger("start");
                        Integer end = classMap.getInteger("end");
                        if (fqcn != null && filePath != null) {
                            declared.add(
                                    new DeclaredClass(
                                            fqcn,
                                            filePath,
                                            start != null ? start : -1,
                                            end != null ? end : -1));
                        }
                    }
                }
            }
        }

        for (DeclaredClass dc : declared) {
            if (!registered.contains(dc.fqcn)) {
                File file = new File(dc.filePath);
                Location location;
                if (dc.start != -1 && dc.end != -1) {
                    Position startPos = new DefaultPosition(-1, -1, dc.start);
                    Position endPos = new DefaultPosition(-1, -1, dc.end);
                    location = Location.create(file, startPos, endPos);
                } else {
                    location = Location.create(file);
                }
                context.report(ISSUE, location, dc.fqcn + " is not registered in the manifest");
            }
        }
    }

    private static class DeclaredClass {
        final String fqcn;
        final String filePath;
        final int start;
        final int end;

        DeclaredClass(String fqcn, String filePath, int start, int end) {
            this.fqcn = fqcn;
            this.filePath = filePath;
            this.start = start;
            this.end = end;
        }
    }
}