package com.github.t1.xml;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.*;
import java.io.*;
import java.net.URI;

import static java.nio.charset.StandardCharsets.*;

public class Xml extends XmlElement {
    private static final URI NIL = URI.create("nil:--");

    public static Xml createWithRootElement(String rootElementName) {
        Document document = newDocument();
        Element rootElement = document.createElement(rootElementName);
        document.appendChild(rootElement);
        return new Xml(document);
    }


    private static Document newDocument() {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public static Xml fromString(String xml) {
        try {
            Document result = DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(UTF_8)));
            return new Xml(result);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Xml load(URI uri) {
        return new Xml(loadDocument(uri));
    }

    private static Document loadDocument(URI uri) {
        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(uri.toASCIIString());
            document.setDocumentURI(uri.toString());
            return document;
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public Xml(Document document) {
        super(null, document.getDocumentElement(), 1);
    }

    @Override
    public URI uri() {
        String uri = document().getDocumentURI();
        return (uri == null) ? NIL : URI.create(uri);
    }

    public Xml uri(URI uri) {
        document().setDocumentURI((uri == null) ? null : uri.toString());
        return this;
    }

    public void save(URI uri) {
        uri(uri);
        save();
    }

    public void save() {
        if (uri() == null)
            throw new IllegalStateException("xml document has no uri save to");
        serializer().writeToURI(document(), uri().toString());
    }
}
