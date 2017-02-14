package com.github.t1.xml;

import lombok.*;
import org.w3c.dom.*;

@RequiredArgsConstructor
@EqualsAndHashCode
public class XmlNode {
    private final XmlElement parent;
    protected final Node node;

    protected Document document() { return node.getOwnerDocument(); }

    protected Comment createComment(String text) { return document().createComment(" " + text + " "); }

    protected Text createText(String text) { return document().createTextNode(text); }

    protected Element createElement(String name) { return document().createElement(name);    }

}
