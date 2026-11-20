package com.github.t1.xml;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.*;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

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
            return newDocumentBuilderFactory().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public static Xml fromString(String xml) {
        try {
            Document result = newDocumentBuilderFactory()
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
            Document document = newDocumentBuilderFactory().newDocumentBuilder().parse(uri.toASCIIString());
            document.setDocumentURI(uri.toString());
            return document;
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private static DocumentBuilderFactory newDocumentBuilderFactory() {
        var factory = DocumentBuilderFactory.newInstance();
        // namespace-aware, so the LS serializer keeps the root element's default xmlns on save
        factory.setNamespaceAware(true);
        return factory;
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
        // write to a string first, so we can append the missing nl after the xml-declaration
        // see https://bugs.openjdk.java.net/browse/JDK-7150637
        StringWriter out = new StringWriter();
        writeTo(out);
        try {
            Files.writeString(Path.of(uri()), out.toString());
        } catch (IOException e) {
            throw new RuntimeException("could not save to " + uri(), e);
        }
    }
}
