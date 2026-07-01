package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlUtils;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.w3c.dom.Element;

public class RegistrationDetector extends Detector implements Detector.ClassScanner, Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "Registered",
            "Class is not registered in the manifest",
            "Activities, services and content providers should be registered in the "
                    + "`AndroidManifest.xml` file using `<activity>`, `<service>` and "
                    + "`<provider>` tags.\n\n"
                    + "If your activity is simply a parent class intended to be subclassed by "
                    + "other \"real\" activities, make it an abstract class.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    RegistrationDetector.class,
                    EnumSet.of(Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE)),
            "https://developer.android.com/guide/topics/manifest/manifest-intro.html");

    private List<String> mRegistrations;

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.TAG_ACTIVITY,
                SdkConstants.TAG_SERVICE,
                SdkConstants.TAG_PROVIDER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = XmlUtils.trimAttributeValue(element.getAttribute(SdkConstants.ATTR_NAME));
        if (name.isEmpty()) {
            return;
        }

        name = resolveManifestName(context, name);
        if (mRegistrations == null) {
            mRegistrations = new ArrayList<>();
        }
        mRegistrations.add(name);
    }

    @Override
    @NonNull
    public List<String> applicableSuperclasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.app.Service",
                "android.content.ContentProvider");
    }

    @Override
    public void checkClass(@NonNull JavaContext context, @NonNull PsiClass classNode) {
        if (classNode.isInterface() || classNode.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        String className = classNode.getQualifiedName();
        if (className == null) {
            return;
        }

        if (mRegistrations != null && mRegistrations.contains(className)) {
            return;
        }

        Location location = context.getNameLocation(classNode);
        String message = String.format("The %1$s is not registered in the manifest", className);
        context.report(ISSUE, classNode, location, message);
    }

    private static String resolveManifestName(@NonNull XmlContext context, @NonNull String name) {
        if (name.isEmpty()) {
            return name;
        }

        String packageName = context.getDocument().getDocumentElement().getAttribute("package");
        if (!packageName.isEmpty()) {
            if (name.charAt(0) == '.') {
                return packageName + name;
            } else if (!name.contains(".")) {
                return packageName + '.' + name;
            }
        }

        return name;
    }
}