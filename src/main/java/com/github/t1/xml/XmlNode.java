package com.github.t1.xml;

import lombok.*;
import org.w3c.dom.*;

@RequiredArgsConstructor
@EqualsAndHashCode
public class XmlNode {
    private final XmlElement parent;
    protected final Node node;

    public Document document() { return node.getOwnerDocument(); }

    protected Comment createComment(String text) { return document().createComment(" " + text + " "); }

    protected Text createText(String text) { return document().createTextNode(text); }

    protected Element createElement(String name) {
        // create in the document's default namespace, so new elements match the existing ones
        String namespaceURI = node.getOwnerDocument().getDocumentElement().getNamespaceURI();
        return namespaceURI == null
            ? document().createElement(name)
            : document().createElementNS(namespaceURI, name);
    }

}
