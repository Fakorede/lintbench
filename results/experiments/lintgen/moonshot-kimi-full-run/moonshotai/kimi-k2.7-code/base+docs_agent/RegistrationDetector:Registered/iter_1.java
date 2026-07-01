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
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.w3c.dom.Element;

public class RegistrationDetector extends Detector
        implements Detector.ClassScanner, Detector.XmlScanner {

    private static final String ACTIVITY = "android/app/Activity";
    private static final String SERVICE = "android/app/Service";
    private static final String PROVIDER = "android/content/ContentProvider";

    public static final Issue ISSUE =
            Issue.create(
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
                            EnumSet.of(Scope.CLASS_FILE_SCOPE),
                            EnumSet.of(Scope.MANIFEST_SCOPE)));

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

    @Override
    public void checkClass(@NonNull ClassContext context, @NonNull ClassNode classNode) {
        if ((classNode.access & Opcodes.ACC_ABSTRACT) != 0) {
            return;
        }
        if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
            return;
        }
        if (classNode.name == null) {
            return;
        }

        if (extendsComponent(context, classNode)) {
            String name = classNode.name.replace('/', '.');
            mCandidates.add(new Candidate(name, context.getLocation(classNode)));
        }
    }

    private static boolean extendsComponent(ClassContext context, ClassNode classNode) {
        ClassNode current = classNode;
        while (current != null && current.superName != null) {
            String superName = current.superName;
            if (superName.equals(ACTIVITY)
                    || superName.equals(SERVICE)
                    || superName.equals(PROVIDER)) {
                return true;
            }
            current = getSuperClass(context, current);
        }
        return false;
    }

    @Nullable
    private static ClassNode getSuperClass(@NonNull ClassContext context, @NonNull ClassNode classNode) {
        String superName = classNode.superName;
        if (superName == null || superName.equals("java/lang/Object")) {
            return null;
        }
        return context.getDriver().getClassNode(superName.replace('/', '.'));
    }

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