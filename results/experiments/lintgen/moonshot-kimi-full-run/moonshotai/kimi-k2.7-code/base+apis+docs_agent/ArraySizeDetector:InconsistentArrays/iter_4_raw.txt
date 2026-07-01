package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ARRAY;
import static com.android.SdkConstants.TAG_INTEGER_ARRAY;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_STRING_ARRAY;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ArraySizeDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistencies in array element counts",
            "When an array is translated in a different locale, it should normally have the same "
                    + "number of elements as the original array. When adding or removing elements "
                    + "to an array, it is easy to forget to update all the locales.\n"
                    + "\n"
                    + "Note however that there may be cases where you really want to declare a "
                    + "different number of array items in each configuration (for example where "
                    + "the array represents available options, and those options differ for "
                    + "different layout orientations and so on), so use your own judgment to "
                    + "decide if this is really an error.\n"
                    + "\n"
                    + "You can suppress this error type if it finds false errors in your project.",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String KEY = ArraySizeDetector.class.getName();
    private static final String FOLDER_VALUES = "values";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ARRAY, TAG_STRING_ARRAY, TAG_INTEGER_ARRAY);
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        if (!context.getDriver().isIncremental()) {
            context.getProject().getClientData().put(KEY, new HashMap<String, Map<String, ArrayInfo>>());
        }
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        Map<String, Map<String, ArrayInfo>> map = getMap(context);
        File file = context.getFile();
        Iterator<Map<String, ArrayInfo>> iterator = map.values().iterator();
        while (iterator.hasNext()) {
            Map<String, ArrayInfo> byFolder = iterator.next();
            Iterator<ArrayInfo> inner = byFolder.values().iterator();
            while (inner.hasNext()) {
                if (file.equals(inner.next().file)) {
                    inner.remove();
                }
            }
            if (byFolder.isEmpty()) {
                iterator.remove();
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (name.isEmpty()) {
            return;
        }

        int size = countItems(element);
        File file = context.file;
        String folder = file.getParentFile().getName();
        String key = element.getNodeName() + "/" + name;

        Map<String, Map<String, ArrayInfo>> map = getMap(context);
        Map<String, ArrayInfo> byFolder = map.get(key);
        if (byFolder == null) {
            byFolder = new HashMap<>();
            map.put(key, byFolder);
        }
        byFolder.put(folder, new ArrayInfo(name, size, folder, file, context.getLocation(element)));
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        Map<String, Map<String, ArrayInfo>> map = getMap(context);
        for (Map<String, ArrayInfo> byFolder : map.values()) {
            if (byFolder.size() < 2) {
                continue;
            }

            ArrayInfo reference = byFolder.get(FOLDER_VALUES);
            if (reference == null) {
                reference = byFolder.values().iterator().next();
            }

            for (ArrayInfo info : byFolder.values()) {
                if (info == reference) {
                    continue;
                }
                if (info.size != reference.size) {
                    String message = String.format(
                            "Array \"%1$s\" has %2$d items in %3$s but %4$d in %5$s",
                            info.name,
                            info.size,
                            info.folder,
                            reference.size,
                            reference.folder);
                    context.report(ISSUE, info.location, message);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, ArrayInfo>> getMap(@NonNull Context context) {
        Project project = context.getProject();
        Map<String, Object> clientData = project.getClientData();
        Object value = clientData.get(KEY);
        if (!(value instanceof Map)) {
            value = new HashMap<String, Map<String, ArrayInfo>>();
            clientData.put(KEY, value);
        }
        return (Map<String, Map<String, ArrayInfo>>) value;
    }

    private static int countItems(@NonNull Element array) {
        int count = 0;
        NodeList children = array.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_ITEM.equals(child.getNodeName())) {
                count++;
            }
        }
        return count;
    }

    private static class ArrayInfo {
        final String name;
        final int size;
        final String folder;
        final File file;
        final Location location;

        ArrayInfo(String name, int size, String folder, File file, Location location) {
            this.name = name;
            this.size = size;
            this.folder = folder;
            this.file = file;
            this.location = location;
        }
    }
}