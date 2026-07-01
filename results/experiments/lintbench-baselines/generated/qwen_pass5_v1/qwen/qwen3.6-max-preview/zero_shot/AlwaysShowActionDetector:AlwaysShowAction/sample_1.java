package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlwaysShowActionDetector extends Detector implements Detector.XmlScanner, Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Using `showAsAction=always`",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in " +
            "Java code is usually a deviation from the user interface style guide. Use `ifRoom` or " +
            "the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n" +
            "If `always` is used sparingly there are usually no problems and behavior is roughly " +
            "equivalent to `ifRoom` but with preference over other `ifRoom` items. Using it more " +
            "than twice in the same menu is a bad idea.\n\n" +
            "This check looks for menu XML files that contain more than two `always` actions, or " +
            "some `always` actions and no `ifRoom` actions. In Java code, it looks for projects that " +
            "contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` and no references to " +
            "`MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
            Category.USABILITY,
            4,
            Severity.WARNING,
            new Implementation(AlwaysShowActionDetector.class, EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)));

    private static final String ALWAYS_COUNT = "alwaysCount";
    private static final String IFROOM_COUNT = "ifRoomCount";
    private static final String FIRST_ALWAYS_ELEMENT = "firstAlwaysElement";
    private static final String SHOW_AS_ACTION = "showAsAction";

    private static class ProjectData {
        int alwaysCount;
        int ifRoomCount;
        Location firstAlwaysLocation;
    }

    private final Map<Project, ProjectData> projectDataMap = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.MENU) {
            return;
        }

        String val = element.getAttributeNS(SdkConstants.ANDROID_URI, SHOW_AS_ACTION);
        if (val == null || val.isEmpty()) {
            val = element.getAttributeNS(SdkConstants.AUTO_URI, SHOW_AS_ACTION);
        }
        if (val == null || val.isEmpty()) {
            return;
        }

        boolean hasAlways = val.contains("always");
        boolean hasIfRoom = val.contains("ifRoom");

        if (hasAlways) {
            Integer count = (Integer) context.getClientProperty(ALWAYS_COUNT);
            context.putClientProperty(ALWAYS_COUNT, count == null ? 1 : count + 1);
            if (context.getClientProperty(FIRST_ALWAYS_ELEMENT) == null) {
                context.putClientProperty(FIRST_ALWAYS_ELEMENT, element);
            }
        }
        if (hasIfRoom) {
            Integer count = (Integer) context.getClientProperty(IFROOM_COUNT);
            context.putClientProperty(IFROOM_COUNT, count == null ? 1 : count + 1);
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }

        Integer always = (Integer) context.getClientProperty(ALWAYS_COUNT);
        Integer ifRoom = (Integer) context.getClientProperty(IFROOM_COUNT);
        int a = always == null ? 0 : always;
        int i = ifRoom == null ? 0 : ifRoom;

        if (a > 2 || (a > 0 && i == 0)) {
            Element first = (Element) context.getClientProperty(FIRST_ALWAYS_ELEMENT);
            Location location = first != null ? ((XmlContext) context).getLocation(first) : Location.create(context.file);
            context.report(ISSUE, location, "Use `ifRoom` instead of `always`");
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitReferenceExpression(@NonNull UReferenceExpression node) {
                String refName = node.getReferenceName();
                if (!"SHOW_AS_ACTION_ALWAYS".equals(refName) && !"SHOW_AS_ACTION_IF_ROOM".equals(refName)) {
                    return;
                }

                PsiElement resolved = node.resolve();
                if (resolved instanceof PsiField) {
                    PsiField field = (PsiField) resolved;
                    String qName = field.getQualifiedName();
                    if (qName == null) return;

                    Project project = context.getProject();
                    ProjectData data = projectDataMap.computeIfAbsent(project, p -> new ProjectData());

                    if (qName.equals("android.view.MenuItem.SHOW_AS_ACTION_ALWAYS")) {
                        data.alwaysCount++;
                        if (data.firstAlwaysLocation == null) {
                            data.firstAlwaysLocation = context.getLocation(node);
                        }
                    } else if (qName.equals("android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM")) {
                        data.ifRoomCount++;
                    }
                }
            }
        };
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        ProjectData data = projectDataMap.remove(context.getProject());
        if (data != null && data.alwaysCount > 0 && data.ifRoomCount == 0) {
            context.report(ISSUE, data.firstAlwaysLocation, "Use `SHOW_AS_ACTION_IF_ROOM` instead of `SHOW_AS_ACTION_ALWAYS`");
        }
    }
}