package org.restlet.ext.jaxrs.provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.restlet.ext.jaxrs.todo.NotYetImplementedException;

/**
 * 
 * @author Stephan Koops
 */
public class XmlTransformSourceProvider extends AbstractProvider<Source> {

    // TODO JSR311: wie geht man mit javax.xml.transform.Source um?

    @Override
    public long getSize(Source object) {
        return -1;
    }

    @Override
    protected boolean isReadableAndWriteable(Class<?> type) {
        // TODO XmlTransformSourceProvider.isReadableAndWriteable(Class)
        return false;
    }

    /**
     * @see org.restlet.ext.jaxrs.provider.AbstractProvider#readFrom(java.lang.Class,
     *      javax.ws.rs.core.MediaType, javax.ws.rs.core.MultivaluedMap,
     *      java.io.InputStream)
     */
    @Override
    public Source readFrom(Class<Source> type, MediaType mediaType,
            MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
            throws IOException {
        return new StreamSource(entityStream);
    }

    @Override
    public void writeTo(Source source, MediaType mediaType,
            MultivaluedMap<String, Object> httpHeaders,
            OutputStream entityStream) throws IOException {
        // StreamResult streamResult = new StreamResult(entityStream);
        // TransformerFactory transformerFactory = TransformerFactory
        //         .newInstance();
        // Source xsltSource = null;
        // Transformer trans = transformerFactory.newTransformer(xsltSource);
        // trans.transform(source, streamResult);
        throw new NotYetImplementedException();
    }
}