package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.utils.Pair;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.HashMap;
import java.util.Map;

public class DuplicateResourceDetector extends ResourceXmlScanner {

    private static final String ISSUE_ID = "DuplicateResources";
    private static final String ISSUE_NAME = "Duplicate resource definitions in the same folder";

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            ISSUE_NAME,
            "Defining the same resource more than once in the same resource folder is likely an error.",
            Category.CORRECTNESS,
            6, // Priority
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @NonNull
    @Override
    public Issue getIssue() {
        return ISSUE;
    }

    @Override
    public void visitDocument(@NonNull ResourceFolderType folderType, @NonNull ResourceType resourceType, @NonNull Document document) {
        Multimap<String, Pair<Integer, String>> resourceMap = HashMultimap.create();
        Element root = document.getDocumentElement();

        NodeList items = root.getElementsByTagName(SdkConstants.ATTR_ITEM);
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);

            String name = item.getAttribute(SdkConstants.ATTR_NAME);
            if (!name.isEmpty()) {
                int lineNumber = getLineNumber(item);
                resourceMap.put(name, Pair.of(lineNumber, item.getTextContent()));
            }
        }

        for (String resourceName : resourceMap.keySet()) {
            Map<Integer, String> occurrences = new HashMap<>();
            for (Pair<Integer, String> occurrence : resourceMap.get(resourceName)) {
                if (!occurrences.containsKey(occurrence.first)) {
                    occurrences.put(occurrence.first, occurrence.second);
                } else {
                    // Report the duplicate
                    report(resourceType, resourceName, lineNumberToOffset(item, occurrence.first));
                }
            }
        }
    }

    private void report(ResourceType resourceType, String resourceName, int offset) {
        context.report(ISSUE,
                context.getLocation(offset),
                "Duplicate definition of resource '" + resourceType.name() + "/" + resourceName + "'");
    }

    private int getLineNumber(Element element) {
        return context.getLineNumber(element);
    }
}