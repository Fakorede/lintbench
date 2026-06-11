package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlwaysShowActionDetector extends Detector implements Detector.XmlScanner, Detector.JavaScanner {
    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Using `showAsAction=always` in menu XML or `MenuItem.SHOW_AS_ACTION_ALWAYS` in Java code is usually a deviation from the user interface style guide.",
            "Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead. Using `always` more than twice in the same menu is a bad idea.",
            Category.USABILITY,
            6,
            Severity.WARNING,
            new Implementation(
                    AlwaysShowActionDetector.class,
                    Scope.JAVA_FILE_SCOPE,
                    Scope.MANIFEST_SCOPE
            )
    );

    private Map<String, Integer> alwaysCount = new HashMap<>();
    private Map<String, Boolean> ifRoomUsed = new HashMap<>();

    @Nullable
    @Override
    public List<JavaUsage> getApplicableUsages(@NonNull JavaContext context) {
        int alwaysReferences = 0;
        boolean ifRoomUsed = false;

        for (JavaUsage usage : context.getUsagesOf("android/view/MenuItem", "SHOW_AS_ACTION_ALWAYS")) {
            alwaysReferences++;
        }

        for (JavaUsage usage : context.getUsagesOf("android/view/MenuItem", "SHOW_AS_ACTION_IF_ROOM")) {
            ifRoomUsed = true;
        }

        String packageName = context.getFile().getPackageName();
        alwaysCount.put(packageName, alwaysReferences);
        this.ifRoomUsed.put(packageName, ifRoomUsed);

        return null;
    }

    @Nullable
    @Override
    public List<Location> checkXmlFile(@NonNull XmlContext context) {
        for (Element element : context.getDomElements(ResourceFolderType.MENU)) {
            String packageName = context.getFile().getPackageName();
            int alwaysCountInMenu = 0;

            for (Attr attr : getAttributes(element)) {
                if ("showAsAction".equals(attr.getName())) {
                    String value = attr.getValue();
                    if ("always".equals(value)) {
                        alwaysCountInMenu++;
                    } else if ("ifRoom".equals(value)) {
                        this.ifRoomUsed.put(packageName, true);
                    }
                }
            }

            int currentAlwaysCount = this.alwaysCount.getOrDefault(packageName, 0) + alwaysCountInMenu;
            this.alwaysCount.put(packageName, currentAlwaysCount);

            if (alwaysCountInMenu > 2 || (!this.ifRoomUsed.getOrDefault(packageName, false) && alwaysCountInMenu > 0)) {
                return List.of(Location.create(context, element));
            }
        }

        return null;
    }

    @Override
    public void afterCheck(@NonNull JavaContext context) {
        String packageName = context.getFile().getPackageName();
        int alwaysReferences = this.alwaysCount.getOrDefault(packageName, 0);
        boolean ifRoomUsed = this.ifRoomUsed.getOrDefault(packageName, false);

        if (alwaysReferences > 2 || (!ifRoomUsed && alwaysReferences > 0)) {
            context.report(ISSUE, context.getLocation(context.getFile()), "Using `showAsAction=always` more than twice or without any `ifRoom` actions is not recommended.");
        }
    }

    @Override
    public void afterCheck(@NonNull XmlContext context) {
        String packageName = context.getFile().getPackageName();
        int alwaysReferences = this.alwaysCount.getOrDefault(packageName, 0);
        boolean ifRoomUsed = this.ifRoomUsed.getOrDefault(packageName, false);

        if (alwaysReferences > 2 || (!ifRoomUsed && alwaysReferences > 0)) {
            context.report(ISSUE, context.getLocation(context.getFile()), "Using `showAsAction=always` more than twice or without any `ifRoom` actions is not recommended.");
        }
    }

    private List<Attr> getAttributes(Element element) {
        return XmlUtils.getAttributeList(element);
    }
}