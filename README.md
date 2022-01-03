# xml [![Maven-Central](https://maven-badges.herokuapp.com/maven-central/com.github.t1/xml/badge.svg)](https://search.maven.org/artifact/com.github.t1/xml)

modern api for xml documents

Xml has a bad reputation for being complex. Part of that reputation comes from the APIs you have to use to work with it.
They haven't changed in years and reflect the coding style that was common 20 years ago.
So it's not fair to compare modern APIs, e.g. JSON-B, with the old APIs, e.g. JAX-B.

This project tries to give a XML a modern, fluent API.
It's not complete or polished, but I use it in some projects for production code.

# sample code

```java
Xml xml = Xml.createWithRootElement("root");
xml.addComment("my-comment");
xml.getOrCreateElement("elem")
    .setAttribute("attr", "val")
    .addText("foo")
    .addText("bar");
```

creates a document:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<root>
    <!-- my-comment -->
    <elem attr="val">foobar</elem>
</root>
```
