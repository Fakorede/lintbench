package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;

import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegistrationDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "Registered",
            "Class is not registered in the manifest",
            "Activities, services and content providers should be registered in the " +
            "`AndroidManifest.xml` file using `<activity>`, `<service>` and " +
            "`<provider>` tags.\n\n" +
            "If your activity is simply a parent class intended to be " +
            "subclassed by other \"real\" activities, make it an abstract class.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    RegistrationDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/guide/topics/manifest/manifest-intro.html");

    /** Map from fully qualified class name to the UClass and JavaContext */
    private final Map<String, ClassEntry> mClassToEntry = new HashMap<>();

    // Android framework superclass FQNs
    private static final String CLASS_ACTIVITY = "android.app.Activity";
    private static final String CLASS_SERVICE = "android.app.Service";
    private static final String CLASS_CONTENT_PROVIDER = "android.content.ContentProvider";
    private static final String CLASS_BROADCAST_RECEIVER = "android.content.BroadcastReceiver";

    /** Map from Android framework superclass to manifest tag */
    private static final Map<String, String> CLASS_TO_TAG = new HashMap<>();

    static {
        CLASS_TO_TAG.put(CLASS_ACTIVITY, SdkConstants.TAG_ACTIVITY);
        CLASS_TO_TAG.put(CLASS_SERVICE, SdkConstants.TAG_SERVICE);
        CLASS_TO_TAG.put(CLASS_CONTENT_PROVIDER, SdkConstants.TAG_PROVIDER);
        CLASS_TO_TAG.put(CLASS_BROADCAST_RECEIVER, SdkConstants.TAG_RECEIVER);
    }

    private static class ClassEntry {
        final UClass uClass;
        final JavaContext context;
        final String tag;

        ClassEntry(UClass uClass, JavaContext context, String tag) {
            this.uClass = uClass;
            this.context = context;
            this.tag = tag;
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                CLASS_ACTIVITY,
                CLASS_SERVICE,
                CLASS_CONTENT_PROVIDER,
                CLASS_BROADCAST_RECEIVER
        );
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        // Skip abstract classes
        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Skip anonymous classes
        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        // Determine which manifest tag this class should be registered under
        String tag = getExpectedTag(context, declaration);
        if (tag == null) {
            return;
        }

        mClassToEntry.put(qualifiedName, new ClassEntry(declaration, context, tag));
    }

    private String getExpectedTag(JavaContext context, UClass declaration) {
        for (Map.Entry<String, String> entry : CLASS_TO_TAG.entrySet()) {
            String superClass = entry.getKey();
            String tag = entry.getValue();
            if (context.getEvaluator().extendsClass(declaration.getJavaPsi(), superClass, false)) {
                return tag;
            }
        }
        return null;
    }

    @Override
    public void afterCheckRootProject(Context context) {
        if (mClassToEntry.isEmpty()) {
            return;
        }

        // Get the manifest file and parse registered components
        Map<String, String> registeredClasses = getRegisteredClasses(context);

        // Check each discovered class against the manifest
        for (Map.Entry<String, ClassEntry> entry : mClassToEntry.entrySet()) {
            String qualifiedName = entry.getKey();
            ClassEntry classEntry = entry.getValue();

            if (!isRegistered(qualifiedName, registeredClasses, classEntry.tag)) {
                String message = String.format(
                        "`%s` is not registered in the manifest",
                        qualifiedName);
                Location location = classEntry.context.getNameLocation(classEntry.uClass);
                classEntry.context.report(ISSUE, classEntry.uClass, location, message);
            }
        }
    }

    private boolean isRegistered(String qualifiedName, Map<String, String> registeredClasses,
            String expectedTag) {
        String registeredTag = registeredClasses.get(qualifiedName);
        if (registeredTag == null) {
            return false;
        }
        return registeredTag.equals(expectedTag);
    }

    private Map<String, String> getRegisteredClasses(Context context) {
        Map<String, String> registered = new HashMap<>();

        com.android.tools.lint.detector.api.Project mainProject = context.getMainProject();

        Document manifest = mainProject.getMergedManifest();
        if (manifest == null) {
            return registered;
        }

        String packageName = mainProject.getPackage();

        String[] tags = {
                SdkConstants.TAG_ACTIVITY,
                SdkConstants.TAG_SERVICE,
                SdkConstants.TAG_PROVIDER,
                SdkConstants.TAG_RECEIVER
        };

        for (String tag : tags) {
            NodeList elements = manifest.getElementsByTagName(tag);
            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);
                String name = element.getAttributeNS(
                        SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if (name == null || name.isEmpty()) {
                    continue;
                }
                String fqn = resolveName(name, packageName);
                if (fqn != null) {
                    registered.put(fqn, tag);
                }
            }
        }

        return registered;
    }

    private String resolveName(String name, String packageName) {
        if (name.startsWith(".")) {
            // Relative name: prepend package
            if (packageName != null) {
                return packageName + name;
            }
            return null;
        } else if (name.contains(".")) {
            // Already fully qualified
            return name;
        } else {
            // Simple name: prepend package
            if (packageName != null) {
                return packageName + "." + name;
            }
            return null;
        }
    }
}