package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RegistrationDetector extends Detector
        implements Detector.SourceCodeScanner, Detector.XmlScanner {

    private static final String ACTIVITY = "android.app.Activity";
    private static final String SERVICE = "android.app.Service";
    private static final String PROVIDER = "android.content.ContentProvider";

    private static final List<String> COMPONENT_TYPES =
            Collections.unmodifiableList(Arrays.asList(ACTIVITY, SERVICE, PROVIDER));

    private static final Map<String, String> COMPONENT_NAMES;
    static {
        Map<String, String> map = new HashMap<>();
        map.put(ACTIVITY, "activity");
        map.put(SERVICE, "service");
        map.put(PROVIDER, "provider");
        COMPONENT_NAMES = Collections.unmodifiableMap(map);
    }

    public static final Issue ISSUE = Issue.create(
            "Registered",
            "Class is not registered in the manifest",
            "Activities, services and content providers should be registered in the "
                    + "`AndroidManifest.xml` file using `<activity>`, `<service>` and "
                    + "`<provider>` tags.\n\n"
                    + "If your activity is simply a parent class intended to be subclassed by other "
                    + "\"real\" activities, make it an abstract class.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(RegistrationDetector.class,
                    EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE)),
            "https://developer.android.com/guide/topics/manifest/manifest-intro.html");

    private final Map<String, String> mClasses = new HashMap<>();
    private final Map<String, Location> mClassLocations = new HashMap<>();
    private final Set<String> mManifestNames = new HashSet<>();
    private String mManifestPackage;

    public RegistrationDetector() {
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUElementTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                PsiClass psiClass = node.getJavaPsi();
                if (psiClass == null
                        || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)
                        || psiClass.isInterface()) {
                    return;
                }

                String qualifiedName = psiClass.getQualifiedName();
                if (qualifiedName == null) {
                    return;
                }

                String componentType = getComponentType(psiClass);
                if (componentType != null) {
                    mClasses.put(qualifiedName, componentType);
                    mClassLocations.put(qualifiedName, context.getLocation(node));
                }
            }
        };
    }

    private String getComponentType(PsiClass psiClass) {
        while (psiClass != null) {
            String name = psiClass.getQualifiedName();
            if (name != null && COMPONENT_TYPES.contains(name)) {
                return name;
            }
            psiClass = psiClass.getSuperClass();
        }
        return null;
    }

    @Override
    public void beforeCheckProject(@NotNull Context context) {
        mClasses.clear();
        mClassLocations.clear();
        mManifestNames.clear();
        mManifestPackage = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("manifest", "activity", "service", "provider");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String tag = element.getTagName();
        if ("manifest".equals(tag)) {
            String packageName = element.getAttribute("package");
            if (packageName != null && !packageName.isEmpty()) {
                mManifestPackage = packageName;
            }
            return;
        }

        if (!COMPONENT_NAMES.containsValue(tag)) {
            return;
        }

        String name = element.getAttribute("android:name");
        if (name == null || name.isEmpty()) {
            return;
        }

        mManifestNames.add(resolveManifestName(name));
    }

    private String resolveManifestName(String name) {
        if (mManifestPackage == null || name == null) {
            return name;
        }
        if (name.startsWith(".")) {
            return mManifestPackage + name;
        }
        if (name.indexOf('.') < 0) {
            return mManifestPackage + "." + name;
        }
        return name;
    }

    @Override
    public void afterCheckEachProject(@NotNull Context context) {
        for (Map.Entry<String, String> entry : mClasses.entrySet()) {
            String className = entry.getKey();
            if (!mManifestNames.contains(className)) {
                String componentType = COMPONENT_NAMES.get(entry.getValue());
                String message = String.format(
                        "The %1$s %2$s is not registered in the AndroidManifest.xml",
                        componentType, className);
                context.report(ISSUE, mClassLocations.get(className), message);
            }
        }
    }
}