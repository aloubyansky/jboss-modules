/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.modules;

import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * A fast, validating module.xml parser.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author thomas.diesler@jboss.com
 */
final class ModuleXmlParser {

    private ModuleXmlParser() {
    }

    private static final String NAMESPACE = "urn:jboss:module:1.0";

    enum Element {
        MODULE,
        DEPENDENCIES,
        EXPORTS,
        IMPORTS,
        INCLUDE,
        EXCLUDE,
        RESOURCES,
        MAIN_CLASS,
        RESOURCE_ROOT,

        // default unknown element
        UNKNOWN;

        private static final Map<QName, Element> elements;

        static {
            Map<QName, Element> elementsMap = new HashMap<QName, Element>();
            elementsMap.put(new QName(NAMESPACE, "module"), Element.MODULE);
            elementsMap.put(new QName(NAMESPACE, "dependencies"), Element.DEPENDENCIES);
            elementsMap.put(new QName(NAMESPACE, "resources"), Element.RESOURCES);
            elementsMap.put(new QName(NAMESPACE, "main-class"), Element.MAIN_CLASS);
            elementsMap.put(new QName(NAMESPACE, "resource-root"), Element.RESOURCE_ROOT);
            elementsMap.put(new QName(NAMESPACE, "exports"), Element.EXPORTS);
            elementsMap.put(new QName(NAMESPACE, "imports"), Element.IMPORTS);
            elementsMap.put(new QName(NAMESPACE, "include"), Element.INCLUDE);
            elementsMap.put(new QName(NAMESPACE, "exclude"), Element.EXCLUDE);
            elements = elementsMap;
        }

        static Element of(QName qName) {
            final Element element = elements.get(qName);
            return element == null ? UNKNOWN : element;
        }
    }

    enum Attribute {
        NAME,
        SLOT,
        EXPORT,
        PATH,
        OPTIONAL,
        
        // default unknown attribute
        UNKNOWN;

        private static final Map<QName, Attribute> attributes;

        static {
            Map<QName, Attribute> attributesMap = new HashMap<QName, Attribute>();
            attributesMap.put(new QName("name"), NAME);
            attributesMap.put(new QName("export"), EXPORT);
            attributesMap.put(new QName("path"), PATH);
            attributesMap.put(new QName("optional"), OPTIONAL);
            attributes = attributesMap;
        }

        static Attribute of(QName qName) {
            final Attribute attribute = attributes.get(qName);
            return attribute == null ? UNKNOWN : attribute;
        }
    }

    static ModuleSpec parse(final ModuleIdentifier moduleIdentifier, final File root, final File moduleInfoFile) throws ModuleLoadException {
        final FileInputStream fis;
        try {
            fis = new FileInputStream(moduleInfoFile);
        } catch (FileNotFoundException e) {
            throw new ModuleLoadException("No module.xml file found at " + moduleInfoFile);
        }
        try {
            return parse(root, fis, moduleInfoFile, moduleIdentifier);
        } finally {
            safeClose(fis);
        }
    }

    private static void setIfSupported(XMLInputFactory inputFactory, String property, Object value) {
        if (inputFactory.isPropertySupported(property)) {
            inputFactory.setProperty(property, value);
        }
    }

    private static ModuleSpec parse(final File root, InputStream source, final File moduleInfoFile, final ModuleIdentifier moduleIdentifier) throws ModuleLoadException {
        try {
            final XMLInputFactory inputFactory = XMLInputFactory.newInstance();
            setIfSupported(inputFactory, XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
            setIfSupported(inputFactory, XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            final XMLStreamReader streamReader = inputFactory.createXMLStreamReader(source);
            try {
                return parseDocument(root, streamReader, ModuleSpec.build(moduleIdentifier));
            } finally {
                safeClose(streamReader);
            }
        } catch (XMLStreamException e) {
            throw new ModuleLoadException("Error loading module from " + moduleInfoFile.getPath(), e);
        }
    }

    private static void safeClose(final Closeable closeable) {
        if (closeable != null) try {
            closeable.close();
        } catch (IOException e) {
            // ignore
        }
    }

    private static void safeClose(final XMLStreamReader streamReader) {
        if (streamReader != null) try {
            streamReader.close();
        } catch (XMLStreamException e) {
            // ignore
        }
    }

    private static XMLStreamException unexpectedContent(final XMLStreamReader reader) {
        final String kind;
        switch (reader.getEventType()) {
            case XMLStreamConstants.ATTRIBUTE: kind = "attribute"; break;
            case XMLStreamConstants.CDATA: kind = "cdata"; break;
            case XMLStreamConstants.CHARACTERS: kind = "characters"; break;
            case XMLStreamConstants.COMMENT: kind = "comment"; break;
            case XMLStreamConstants.DTD: kind = "dtd"; break;
            case XMLStreamConstants.END_DOCUMENT: kind = "document end"; break;
            case XMLStreamConstants.END_ELEMENT: kind = "element end"; break;
            case XMLStreamConstants.ENTITY_DECLARATION: kind = "entity declaration"; break;
            case XMLStreamConstants.ENTITY_REFERENCE: kind = "entity ref"; break;
            case XMLStreamConstants.NAMESPACE: kind = "namespace"; break;
            case XMLStreamConstants.NOTATION_DECLARATION: kind = "notation declaration"; break;
            case XMLStreamConstants.PROCESSING_INSTRUCTION: kind = "processing instruction"; break;
            case XMLStreamConstants.SPACE: kind = "whitespace"; break;
            case XMLStreamConstants.START_DOCUMENT: kind = "document start"; break;
            case XMLStreamConstants.START_ELEMENT: kind = "element start"; break;
            default: kind = "unknown"; break;
        }
        final StringBuilder b = new StringBuilder("Unexpected content of type '").append(kind).append('\'');
        if (reader.hasName()) {
            b.append(" named '").append(reader.getName()).append('\'');
        }
        if (reader.hasText()) {
            b.append(", text is: '").append(reader.getText()).append('\'');
        }
        return new XMLStreamException(b.toString(), reader.getLocation());
    }

    private static XMLStreamException endOfDocument(final Location location) {
        return new XMLStreamException("Unexpected end of document", location);
    }

    private static XMLStreamException invalidModuleName(final Location location, final ModuleIdentifier expected) {
        return new XMLStreamException("Invalid/mismatched module name (expected " + expected + ")", location);
    }

    private static XMLStreamException missingAttributes(final Location location, final Set<Attribute> required) {
        final StringBuilder b = new StringBuilder("Missing one or more required attributes:");
        for (Attribute attribute : required) {
            b.append(' ').append(attribute);
        }
        return new XMLStreamException(b.toString(), location);
    }

    private static ModuleSpec parseDocument(final File root, XMLStreamReader reader, ModuleSpec.Builder specBuilder) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.START_DOCUMENT: {
                    parseRootElement(root, reader, specBuilder);
                    return specBuilder.create();
                }
                case XMLStreamConstants.START_ELEMENT: {
                    if (Element.of(reader.getName()) != Element.MODULE) {
                        throw unexpectedContent(reader);
                    }
                    parseModuleContents(root, reader, specBuilder);
                    parseEndDocument(reader);
                    return specBuilder.create();
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
        throw endOfDocument(reader.getLocation());
    }

    private static void parseRootElement(final File root, final XMLStreamReader reader, final ModuleSpec.Builder specBuilder) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.START_ELEMENT: {
                    if (Element.of(reader.getName()) != Element.MODULE) {
                        throw unexpectedContent(reader);
                    }
                    parseModuleContents(root, reader, specBuilder);
                    parseEndDocument(reader);
                    return;
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
        throw endOfDocument(reader.getLocation());
    }

    private static void parseModuleContents(final File root, final XMLStreamReader reader, final ModuleSpec.Builder specBuilder) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        String name = null;
        String slot = null;
        final Set<Attribute> required = EnumSet.of(Attribute.NAME);
        for (int i = 0; i < count; i ++) {
            final Attribute attribute = Attribute.of(reader.getAttributeName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME:    name = reader.getAttributeValue(i); break;
                case SLOT:    slot = reader.getAttributeValue(i); break;
                default: throw unexpectedContent(reader);
            }
        }
        if (! required.isEmpty()) {
            throw missingAttributes(reader.getLocation(), required);
        }
        if (! specBuilder.getIdentifier().equals(ModuleIdentifier.create(name, slot))) {
            throw invalidModuleName(reader.getLocation(), specBuilder.getIdentifier());
        }
        // xsd:all
        Set<Element> visited = EnumSet.noneOf(Element.class);
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.END_ELEMENT: {
                    specBuilder.addDependency(DependencySpec.createLocalDependencySpec());
                    return;
                }
                case XMLStreamConstants.START_ELEMENT: {
                    final Element element = Element.of(reader.getName());
                    if (visited.contains(element)) {
                        throw unexpectedContent(reader);
                    }
                    visited.add(element);
                    switch (element) {
                        case DEPENDENCIES: parseDependencies(reader, specBuilder); break;
                        case MAIN_CLASS:   parseMainClass(reader, specBuilder); break;
                        case RESOURCES:    parseResources(root, reader, specBuilder); break;
                        default: throw unexpectedContent(reader);
                    }
                    break;
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
        throw endOfDocument(reader.getLocation());
    }

    private static void parseDependencies(final XMLStreamReader reader, final ModuleSpec.Builder specBuilder) throws XMLStreamException {
        // xsd:choice
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.END_ELEMENT: {
                    return;
                }
                case XMLStreamConstants.START_ELEMENT: {
                    switch (Element.of(reader.getName())) {
                        case MODULE: parseModuleDependency(reader, specBuilder); break;
                        default: throw unexpectedContent(reader);
                    }
                    break;
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
        throw endOfDocument(reader.getLocation());
    }

    private static void parseModuleDependency(final XMLStreamReader reader, final ModuleSpec.Builder specBuilder) throws XMLStreamException {
        String name = null;
        String slot = null;
        boolean export = false;
        boolean optional = false;
        final Set<Attribute> required = EnumSet.of(Attribute.NAME);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i ++) {
            final Attribute attribute = Attribute.of(reader.getAttributeName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME:    name = reader.getAttributeValue(i); break;
                case SLOT:    slot = reader.getAttributeValue(i); break;
                case EXPORT:  export = Boolean.parseBoolean(reader.getAttributeValue(i)); break;
                case OPTIONAL:optional = Boolean.parseBoolean(reader.getAttributeValue(i)); break;
                default: throw unexpectedContent(reader);
            }
        }
        if (! required.isEmpty()) {
            throw missingAttributes(reader.getLocation(), required);
        }
        final MultiplePathFilterBuilder importBuilder = PathFilters.multiplePathFilterBuilder(true);
        final MultiplePathFilterBuilder exportBuilder = PathFilters.multiplePathFilterBuilder(true);
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.END_ELEMENT: {
                    final PathFilter exportFilter = export ? exportBuilder.create() : PathFilters.rejectAll();
                    final PathFilter importFilter = importBuilder.create();
                    specBuilder.addDependency(DependencySpec.createModuleDependencySpec(importFilter, exportFilter, null, ModuleIdentifier.create(name, slot), optional));
                    return;
                }
                case XMLStreamConstants.START_ELEMENT: {
                    switch (Element.of(reader.getName())) {
                        case EXPORTS: parseFilterList(reader, exportBuilder); break;
                        case IMPORTS: parseFilterList(reader, importBuilder); break;
                        default: throw unexpectedContent(reader);
                    }
                    break;
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
    }

    private static void parseMainClass(final XMLStreamReader reader, final ModuleSpec.Builder specBuilder) throws XMLStreamException {
        String name = null;
        final Set<Attribute> required = EnumSet.of(Attribute.NAME);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i ++) {
            final Attribute attribute = Attribute.of(reader.getAttributeName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: name = reader.getAttributeValue(i); break;
                default: throw unexpectedContent(reader);
            }
        }
        if (! required.isEmpty()) {
            throw missingAttributes(reader.getLocation(), required);
        }
        specBuilder.setMainClass(name);
        // consume remainder of element
        parseNoContent(reader);
    }

    private static void parseResources(final File root, final XMLStreamReader reader, final ModuleSpec.Builder specBuilder) throws XMLStreamException {
        // xsd:choice
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.END_ELEMENT: {
                    return;
                }
                case XMLStreamConstants.START_ELEMENT: {
                    switch (Element.of(reader.getName())) {
                        case RESOURCE_ROOT: {
                            parseResourceRoot(root, reader, specBuilder);
                            break;
                        }
                        default: throw unexpectedContent(reader);
                    }
                    break;
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
        throw endOfDocument(reader.getLocation());
    }

    private static void parseResourceRoot(final File root, final XMLStreamReader reader, final ModuleSpec.Builder specBuilder) throws XMLStreamException {
        final ModuleIdentifier identifier = specBuilder.getIdentifier();
        String name = null;
        String path = null;
        final Set<Attribute> required = EnumSet.of(Attribute.PATH);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i ++) {
            final Attribute attribute = Attribute.of(reader.getAttributeName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: name = reader.getAttributeValue(i); break;
                case PATH: path = reader.getAttributeValue(i); break;
                default: throw unexpectedContent(reader);
            }
        }
        if (! required.isEmpty()) {
            throw missingAttributes(reader.getLocation(), required);
        }
        if (name == null) name = path;
        // todo add to spec
        final File file = new File(root, path);

        final ResourceLoader resourceLoader;
        final MultiplePathFilterBuilder builder = PathFilters.multiplePathFilterBuilder(true);

        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.END_ELEMENT: {
                    final PathFilter exportFilter = builder.create();
                    if (file.isDirectory()) {
                        resourceLoader = new FileResourceLoader(identifier, file, name, exportFilter);
                    } else {
                        try {
                            resourceLoader = new JarFileResourceLoader(identifier, new JarFile(file), name, exportFilter);
                        } catch (IOException e) {
                            throw new XMLStreamException("Invalid JAR file specified", reader.getLocation(), e);
                        }
                    }
                    specBuilder.addResourceRoot(resourceLoader);
                    return;
                }
                case XMLStreamConstants.START_ELEMENT: {
                    switch (Element.of(reader.getName())) {
                        case EXPORTS: parseFilterList(reader, builder); break;
                        default: throw unexpectedContent(reader);
                    }
                    break;
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
    }

    private static void parseFilterList(final XMLStreamReader reader, final MultiplePathFilterBuilder builder) throws XMLStreamException {
        // xsd:choice
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.END_ELEMENT: {
                    return;
                }
                case XMLStreamConstants.START_ELEMENT: {
                    switch (Element.of(reader.getName())) {
                        case INCLUDE: parsePath(reader, true, builder); break;
                        case EXCLUDE: parsePath(reader, false, builder);  break;
                        default: throw unexpectedContent(reader);
                    }
                    break;
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
        throw endOfDocument(reader.getLocation());
    }

    private static void parsePath(final XMLStreamReader reader, final boolean include, final MultiplePathFilterBuilder builder) throws XMLStreamException {
        String path = null;
        final Set<Attribute> required = EnumSet.of(Attribute.PATH);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i ++) {
            final Attribute attribute = Attribute.of(reader.getAttributeName(i));
            required.remove(attribute);
            switch (attribute) {
                case PATH: path = reader.getAttributeValue(i); break;
                default: throw unexpectedContent(reader);
            }
        }
        if (! required.isEmpty()) {
            throw missingAttributes(reader.getLocation(), required);
        }

        builder.addFilter(PathFilters.match(path), include);

        // consume remainder of element
        parseNoContent(reader);
    }

    private static void parseNoContent(final XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.END_ELEMENT: {
                    return;
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
        throw endOfDocument(reader.getLocation());
    }

    private static void parseEndDocument(final XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.END_DOCUMENT: {
                    return;
                }
                case XMLStreamConstants.CHARACTERS: {
                    if (! reader.isWhiteSpace()) {
                        throw unexpectedContent(reader);
                    }
                    // ignore
                    break;
                }
                case XMLStreamConstants.COMMENT:
                case XMLStreamConstants.SPACE: {
                    // ignore
                    break;
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
        return;
    }
}
