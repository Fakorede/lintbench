package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
    private static final String CLASS_RECEIVER = "android.content.BroadcastReceiver";

    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_PROVIDER = "provider";
    private static final String TAG_RECEIVER = "receiver";

    private static final String ANDROID_NAME = "android:name";

    private static final String CLASS_NAMES = "classNames";
    private static final String COMPONENT_NAMES = "componentNames";

    private static final Implementation IMPLEMENTATION =
            new Implementation(RegistrationDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "Registered",
                    "Class is not registered in the manifest",
                    "Activities, services, content providers and broadcast receivers should be registered in the AndroidManifest.xml file using `<activity>`, `<service>`, `<provider>` and `<receiver>` tags.\n\nIf your activity is simply a parent class intended to be subclassed by other \"real\" activities, make it an abstract class.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(CLASS_ACTIVITY, CLASS_SERVICE, CLASS_PROVIDER, CLASS_RECEIVER);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass == null || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        Location location = context.getLocation(declaration);
        int line = 0;
        if (location != null && location.getStart() != null) {
            line = location.getStart().getLine();
        }

        com.google.gson.JsonObject record = new com.google.gson.JsonObject();
        record.addProperty("name", qualifiedName);
        record.addProperty("file", context.file.getPath());
        record.addProperty("line", line);

        recordClassRecord(context, record);
    }

    @Override
    public Collection<String> applicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_SERVICE, TAG_PROVIDER, TAG_RECEIVER);
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String name = element.getAttribute(ANDROID_NAME);
        if (name == null || name.isEmpty() || name.startsWith("@")) {
            return;
        }

        String pkg = context.getMainProject().getPackage();
        if (pkg != null && !pkg.isEmpty()) {
            if (name.startsWith(".")) {
                name = pkg + name;
            } else if (name.indexOf('.') == -1) {
                name = pkg + "." + name;
            }
        }

        recordComponentName(context, name);
    }

    @Override
    public void checkPartialResults(Context context, PartialResult partialResults) {
        Map<String, com.google.gson.JsonObject> map = partialResults.map();

        Set<String> components = new HashSet<>();
        List<com.google.gson.JsonObject> classRecords = new ArrayList<>();

        for (com.google.gson.JsonObject fileObject : map.values()) {
            com.google.gson.JsonElement compElement = fileObject.get(COMPONENT_NAMES);
            if (compElement != null && compElement.isJsonArray()) {
                com.google.gson.JsonArray compArray = compElement.getAsJsonArray();
                for (com.google.gson.JsonElement e : compArray) {
                    if (e != null && e.isJsonPrimitive()) {
                        components.add(e.getAsString());
                    }
                }
            }

            com.google.gson.JsonElement classElement = fileObject.get(CLASS_NAMES);
            if (classElement != null && classElement.isJsonArray()) {
                com.google.gson.JsonArray classArray = classElement.getAsJsonArray();
                for (com.google.gson.JsonElement e : classArray) {
                    if (e != null && e.isJsonObject()) {
                        classRecords.add(e.getAsJsonObject());
                    }
                }
            }
        }

        for (com.google.gson.JsonObject record : classRecords) {
            com.google.gson.JsonElement nameElement = record.get("name");
            if (nameElement == null || !nameElement.isJsonPrimitive()) {
                continue;
            }
            String name = nameElement.getAsString();
            if (components.contains(name)) {
                continue;
            }

            com.google.gson.JsonElement fileElement = record.get("file");
            if (fileElement == null || !fileElement.isJsonPrimitive()) {
                continue;
            }
            java.io.File file = new java.io.File(fileElement.getAsString());

            com.google.gson.JsonElement lineElement = record.get("line");
            int line = lineElement != null ? lineElement.getAsInt() : 0;

            Location location = context.getRangeLocation(file, line, 0, line, Integer.MAX_VALUE);
            context.report(ISSUE, location, "Class " + name + " is not registered in the manifest");
        }
    }

    private static void recordClassRecord(Context context, com.google.gson.JsonObject record) {
        PartialResult result = context.getPartialResults(ISSUE);
        Map<String, com.google.gson.JsonObject> map = result.map();
        String path = context.file.getPath();
        com.google.gson.JsonObject fileObject = map.get(path);
        if (fileObject == null) {
            fileObject = new com.google.gson.JsonObject();
            map.put(path, fileObject);
        }
        com.google.gson.JsonElement classElement = fileObject.get(CLASS_NAMES);
        com.google.gson.JsonArray classArray;
        if (classElement == null || classElement.isJsonNull()) {
            classArray = new com.google.gson.JsonArray();
            fileObject.add(CLASS_NAMES, classArray);
        } else {
            classArray = classElement.getAsJsonArray();
        }
        classArray.add(record);
    }

    private static void recordComponentName(Context context, String name) {
        PartialResult result = context.getPartialResults(ISSUE);
        Map<String, com.google.gson.JsonObject> map = result.map();
        String path = context.file.getPath();
        com.google.gson.JsonObject fileObject = map.get(path);
        if (fileObject == null) {
            fileObject = new com.google.gson.JsonObject();
            map.put(path, fileObject);
        }
        com.google.gson.JsonElement compElement = fileObject.get(COMPONENT_NAMES);
        com.google.gson.JsonArray compArray;
        if (compElement == null || compElement.isJsonNull()) {
            compArray = new com.google.gson.JsonArray();
            fileObject.add(COMPONENT_NAMES, compArray);
        } else {
            compArray = compElement.getAsJsonArray();
        }
        compArray.add(name);
    }
}