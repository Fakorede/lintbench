package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_MANIFEST;
import static com.android.SdkConstants.TAG_PROVIDER;
import static com.android.SdkConstants.TAG_SERVICE;

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
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.visitor.UElementHandler;
import org.w3c.dom.Element;

public class RegistrationDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String ACTIVITY = "android.app.Activity";
    private static final String APPCOMPAT_ACTIVITY = "android.support.v7.app.AppCompatActivity";
    private static final String ANDROIDX_APPCOMPAT_ACTIVITY =
            "androidx.appcompat.app.AppCompatActivity";
    private static final String SERVICE = "android.app.Service";
    private static final String PROVIDER = "android.content.ContentProvider";

    private static final Set<String> COMPONENT_CLASSES;
    static {
        Set<String> set = new HashSet<>();
        set.add(ACTIVITY);
        set.add(APPCOMPAT_ACTIVITY);
        set.add(ANDROIDX_APPCOMPAT_ACTIVITY);
        set.add(SERVICE);
        set.add(PROVIDER);
        COMPONENT_CLASSES = Collections.unmodifiableSet(set);
    }

    public static final Issue ISSUE = Issue.create(
            "Registered",
            "Class is not registered in the manifest",
            "Activities, services and content providers should be registered in the "
                    + "`AndroidManifest.xml` file using `<activity>`, `<service>` and "
                    + "`<provider>` tags.\n\n"
                    + "If your activity is simply a parent class intended to be subclassed "
                    + "by other \"real\" activities, make it an abstract class.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    RegistrationDetector.class,
                    Scope.JAVA_FILE_SCOPE,
                    Scope.MANIFEST)
    );

    private static class Candidate {
        final String name;
        final Location location;

        Candidate(String name, Location location) {
            this.name = name;
            this.location = location;
        }
    }

    private final List<Candidate> mCandidates = new ArrayList<>();
    private final Set<String> mRegisteredNames = new HashSet<>();
    private String mManifestPackage;

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mCandidates.clear();
        mRegisteredNames.clear();
        mManifestPackage = null;
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (Candidate candidate : mCandidates) {
            if (!mRegisteredNames.contains(candidate.name)) {
                context.report(
                        ISSUE,
                        candidate.location,
                        "Class " + candidate.name + " is not registered in the manifest");
            }
        }
    }

    // ---- SourceCodeScanner ----

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                checkClass(context, node);
            }
        };
    }

    private void checkClass(@NonNull JavaContext context, @NonNull UClass node) {
        PsiClass psiClass = node.getJavaPsi();
        if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        String name = node.getQualifiedName();
        if (name == null) {
            return;
        }

        if (extendsComponent(node)) {
            mCandidates.add(new Candidate(name, context.getNameLocation(node)));
        }
    }

    private static boolean extendsComponent(@NonNull UClass node) {
        PsiClass current = node.getJavaPsi().getSuperClass();
        while (current != null) {
            String qualifiedName = current.getQualifiedName();
            if (qualifiedName != null && COMPONENT_CLASSES.contains(qualifiedName)) {
                return true;
            }
            current = current.getSuperClass();
        }
        return false;
    }

    // ---- XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_MANIFEST, TAG_ACTIVITY, TAG_SERVICE, TAG_PROVIDER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_MANIFEST.equals(tag)) {
            String pkg = element.getAttribute(ATTR_PACKAGE);
            if (pkg != null && !pkg.isEmpty()) {
                mManifestPackage = pkg;
            }
            return;
        }

        String className = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (className == null || className.isEmpty()) {
            return;
        }

        if (mManifestPackage == null || mManifestPackage.isEmpty()) {
            mManifestPackage = context.getProject().getPackage();
        }

        mRegisteredNames.add(resolveClassName(className, mManifestPackage));
    }

    private static String resolveClassName(@NonNull String name, @Nullable String packageName) {
        if (name.startsWith(".")) {
            if (packageName == null || packageName.isEmpty()) {
                return name;
            }
            return packageName + name;
        }

        if (name.indexOf('.') == -1) {
            if (packageName == null || packageName.isEmpty()) {
                return name;
            }
            return packageName + '.' + name;
        }

        return name;
    }
}