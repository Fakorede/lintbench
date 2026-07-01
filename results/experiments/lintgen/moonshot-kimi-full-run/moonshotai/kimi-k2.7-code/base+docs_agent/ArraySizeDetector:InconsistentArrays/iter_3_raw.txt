package com.android.tools.lint.checks;

import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.ide.common.resources.ResourceFile;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.resourceValue.ArrayResourceValue;
import com.android.ide.common.resources.resourceValue.ResourceValue;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ArraySizeDetector extends Detector implements XmlScanner {

    private static final String TAG_STRING_ARRAY = "string-array";
    private static final String TAG_INTEGER_ARRAY = "integer-array";
    private static final String TAG_ARRAY = "array";
    private static final String TAG_ITEM = "item";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_TRANSLATABLE = "translatable";

    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistent array sizes",
            "When an array is translated in a different locale, it should normally have the same number of elements as the original array. When adding or removing elements to an array, it is easy to forget to update all the locales, and this lint warning finds inconsistencies like these.\n\nNote however that there may be cases where you really want to declare a different number of array items in each configuration (for example where the array represents available options, and those options differ for different layout orientations and so on), so use your own judgment to decide if this is really an error.\n\nYou can suppress this error type if it finds false errors in your project.",
            Category.I18N,
            3,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    @NotNull
    public List<String> getApplicableElements() {
        return Arrays.asList(TAG_STRING_ARRAY, TAG_INTEGER_ARRAY, TAG_ARRAY);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String tag = element.getTagName();
        if (!TAG_STRING_ARRAY.equals(tag)
                && !TAG_INTEGER_ARRAY.equals(tag)
                && !TAG_ARRAY.equals(tag)) {
            return;
        }

        String name = element.getAttribute(ATTR_NAME);
        if (name.isEmpty()) {
            return;
        }

        if ("false".equals(element.getAttribute(ATTR_TRANSLATABLE))) {
            return;
        }

        java.io.File parentFile = context.file.getParentFile();
        if (parentFile == null) {
            return;
        }

        ResourceFolderType folder = ResourceFolderType.getFolderType(parentFile.getName());
        if (folder != ResourceFolderType.VALUES) {
            return;
        }

        AbstractResourceRepository resources = context.getProject().getResourceRepository();
        if (resources == null) {
            return;
        }

        int currentCount = getItemCount(element);
        String currentConfig = parentFile.getName();

        List<ResourceItem> items = resources.getResourceItem(ResourceType.ARRAY, name);
        if (items == null) {
            return;
        }

        boolean mismatch = false;
        for (ResourceItem item : items) {
            ResourceFile source = item.getSource();
            if (source == null) {
                continue;
            }
            String config = source.getFile().getParentFile().getName();
            if (currentConfig.equals(config)) {
                continue;
            }
            ResourceValue value = item.getValue();
            if (value instanceof ArrayResourceValue) {
                int count = ((ArrayResourceValue) value).getValueCount();
                if (count != currentCount) {
                    mismatch = true;
                    break;
                }
            }
        }

        if (mismatch) {
            String message = String.format(Locale.US,
                    "Array \"%s\" has %d items in %s", name, currentCount, currentConfig);
            context.report(ISSUE, context.getLocation(element), message);
        }
    }

    private static int getItemCount(Element element) {
        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_ITEM.equals(child.getNodeName())) {
                count++;
            }
        }
        return count;
    }
}