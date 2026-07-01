package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RegistrationDetector extends Detector implements Detector.ClassScanner,
        Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            RegistrationDetector.class,
            Scope.CLASS_FILE_SCOPE,
            Scope.MANIFEST_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            "Registered",
            "Class is not registered in the manifest",
            "Activities, services and content providers should be registered in the "
                    + "AndroidManifest.xml file using `<activity>`, `<service>` and `<provider>` "
                    + "tags.\n\n"
                    + "If your activity is simply a parent class intended to be subclassed by "
                    + "other \"real\" activities, make it an abstract class.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            IMPLEMENTATION
    );

    private final Set<String> mRegisteredClasses = new HashSet<>();

    @Override
    @NotNull
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android/app/Activity",
                "android/app/Service",
                "android/content/ContentProvider"
        );
    }

    @Override
    public void checkClass(@NotNull ClassContext context, @NotNull ClassNode classNode) {
        if ((classNode.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE)) != 0) {
            return;
        }

        String name = classNode.name.replace('/', '.');
        if (mRegisteredClasses.contains(name)) {
            return;
        }

        String message = String.format("The %1$s is not registered in the manifest", name);
        Location location = Location.create(context.file);
        context.report(ISSUE, location, message);
    }

    @Override
    @NotNull
    public List<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.TAG_ACTIVITY,
                SdkConstants.TAG_SERVICE,
                SdkConstants.TAG_PROVIDER
        );
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String className = element.getAttributeNS(SdkConstants.ANDROID_URI,
                SdkConstants.ATTR_NAME);
        if (className == null || className.isEmpty()) {
            return;
        }

        String packageName = context.getMainProject().getPackage();
        String fullClassName = getFullClassName(packageName, className);
        if (fullClassName != null) {
            mRegisteredClasses.add(fullClassName);
        }
    }

    private static String getFullClassName(String packageName, String className) {
        if (className.startsWith(".")) {
            return packageName + className;
        }
        if (packageName != null && !packageName.isEmpty() && !className.contains(".")) {
            return packageName + "." + className;
        }
        return className;
    }
}