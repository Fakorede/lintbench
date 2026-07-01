package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.UElementHandler;
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RegistrationDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "Registered",
            "Class is not registered in the manifest",
            "Activities, services and content providers should be registered in the " +
                    "`AndroidManifest.xml` file using `<activity>`, `<service>` and `<provider>` tags.\n\n" +
                    "If your activity is simply a parent class intended to be subclassed by other " +
                    "\"real\" activities, make it an abstract class.",
            Category.CORRECTNESS,
            8,
            Severity.FATAL,
            new Implementation(RegistrationDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST))
    );

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_PROVIDER = "provider";
    private static final String TAG_ACTIVITY_ALIAS = "activity-alias";

    private static final String ATTR_NAME = "name";
    private static final String ATTR_TARGET_ACTIVITY = "targetActivity";

    private static final String CLASS_ACTIVITY = "android.app.Activity";
    private static final String CLASS_SERVICE = "android.app.Service";
    private static final String CLASS_CONTENT_PROVIDER = "android.content.ContentProvider";

    private final Set<String> mManifestComponents = new HashSet<>();
    private final List<Candidate> mCandidates = new ArrayList<>();
    private String mManifestPackage;

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass cls) {
                PsiClass psi = cls.getJavaPsi();
                if (psi == null || psi.isInterface() || psi.isEnum() || psi.isAnnotationType()) {
                    return;
                }

                if (psi.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                boolean isComponent = evaluator.extendsClass(psi, CLASS_ACTIVITY, false)
                        || evaluator.extendsClass(psi, CLASS_SERVICE, false)
                        || evaluator.extendsClass(psi, CLASS_CONTENT_PROVIDER, false);

                if (!isComponent) {
                    return;
                }

                String displayName = psi.getQualifiedName();
                Set<String> names = getCandidateNames(psi);
                if (displayName != null && !names.isEmpty()) {
                    mCandidates.add(new Candidate(displayName, names, context,
                            context.getNameLocation(cls)));
                }
            }
        };
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_SERVICE, TAG_PROVIDER, TAG_ACTIVITY_ALIAS);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        String name;
        if (TAG_ACTIVITY_ALIAS.equals(tag)) {
            name = element.getAttributeNS(ANDROID_URI, ATTR_TARGET_ACTIVITY);
        } else {
            name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        }

        String fqcn = getFullyQualifiedClassName(mManifestPackage, name);
        if (fqcn != null) {
            mManifestComponents.add(fqcn);
        }
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        mManifestPackage = root != null ? root.getAttribute("package") : null;
    }

    @Override
    public void beforeCheckEachProject(Context context) {
        mManifestComponents.clear();
        mCandidates.clear();
        mManifestPackage = null;
    }

    @Override
    public void afterCheckEachProject(Context context) {
        for (Candidate candidate : mCandidates) {
            if (Collections.disjoint(candidate.names, mManifestComponents)) {
                candidate.context.report(
                        ISSUE,
                        candidate.location,
                        candidate.name + " is not registered in the manifest"
                );
            }
        }
    }

    private static String getFullyQualifiedClassName(String packageName, String className) {
        if (className == null || className.isEmpty()) {
            return null;
        }
        if (className.startsWith(".")) {
            return packageName != null ? packageName + className : null;
        }
        if (className.indexOf('.') == -1) {
            if (packageName != null && !packageName.isEmpty()) {
                return packageName + "." + className;
            }
            return className;
        }
        return className;
    }

    private static Set<String> getCandidateNames(PsiClass psi) {
        Set<String> names = new HashSet<>();
        String dotFqcn = psi.getQualifiedName();
        if (dotFqcn != null) {
            names.add(dotFqcn);
        }
        String binaryFqcn = getBinaryFqcn(psi);
        if (binaryFqcn != null) {
            names.add(binaryFqcn);
        }
        return names;
    }

    private static String getBinaryFqcn(PsiClass psi) {
        String packageName = getPackageName(psi);
        String binaryClassName = getBinaryClassName(psi);
        if (binaryClassName == null) {
            return null;
        }
        if (packageName != null && !packageName.isEmpty()) {
            return packageName + "." + binaryClassName;
        }
        return binaryClassName;
    }

    private static String getBinaryClassName(PsiClass psi) {
        String name = psi.getName();
        if (name == null) {
            return null;
        }
        PsiClass containing = psi.getContainingClass();
        if (containing != null) {
            String outer = getBinaryClassName(containing);
            return outer != null ? outer + "$" + name : null;
        }
        return name;
    }

    private static String getPackageName(PsiClass psi) {
        PsiClass containing = psi.getContainingClass();
        if (containing != null) {
            return getPackageName(containing);
        }
        String qn = psi.getQualifiedName();
        String name = psi.getName();
        if (qn == null || name == null) {
            return null;
        }
        int index = qn.lastIndexOf("." + name);
        if (index >= 0) {
            return qn.substring(0, index);
        }
        return null;
    }

    private static class Candidate {
        final String name;
        final Set<String> names;
        final JavaContext context;
        final Location location;

        Candidate(String name, Set<String> names, JavaContext context, Location location) {
            this.name = name;
            this.names = names;
            this.context = context;
            this.location = location;
        }
    }
}