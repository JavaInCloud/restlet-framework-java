/*
 * Copyright 2005-2008 Noelios Consulting.
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the "License"). You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL HEADER in each file and
 * include the License file at http://www.opensource.org/licenses/cddl1.txt If
 * applicable, add the following below this CDDL HEADER, with the fields
 * enclosed by brackets "[]" replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */
package org.restlet.ext.jaxrs.internal.provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;

import org.restlet.ext.jaxrs.internal.util.Util;

/**
 * This abstract class ease the development of {@link MessageBodyReader}s and
 * {@link MessageBodyWriter}.
 * 
 * @author Stephan Koops
 * @param <T>
 *                the type that can be read and written
 * @see MessageBodyReader
 * @see MessageBodyWriter
 */
public abstract class AbstractProvider<T> implements MessageBodyWriter<T>,
        MessageBodyReader<T> {

    /**
     * Logs the problem and throws an IOException.
     * 
     * @param logger
     * @param message
     * @param exc
     * @throws IOException
     */
    protected static IOException logAndIOExc(Logger logger, String message,
            Throwable exc) throws IOException {
        logger.log(Level.WARNING, message, exc);
        if (exc == null)
            throw new IOException(message);
        throw new IOException(message + ": " + exc.getMessage());
    }

    protected void copyAndCloseStream(InputStream inputStream,
            OutputStream outputStream) throws IOException {
        try {
            Util.copyStream(inputStream, outputStream);
        } finally {
            inputStream.close();
        }
    }

    /**
     * Returns the size of the given objects.
     * 
     * @param object
     *                the object to check the size
     * @return the size of the object, or -1, if it is not direct readable from
     *         the object.
     * @see MessageBodyWriter#getSize(Object)
     */
    public abstract long getSize(T object);

    public boolean isReadable(Class<?> type, Type genericType,
            Annotation[] annotations) {
        return type.isAssignableFrom(supportedClass());
    }

    public boolean isWriteable(Class<?> type, Type genericType,
            Annotation[] annotations) {
        return supportedClass().isAssignableFrom(type);
        // mainClass.isAssignableFrom(subClass);
    }

    /**
     * @param genericType
     *                The generic {@link Type} to convert to.
     * @param annotations
     *                the annotations of the artefact to convert to
     * @see javax.ws.rs.ext.MessageBodyReader#readFrom(java.lang.Class,
     *      javax.ws.rs.core.MediaType, javax.ws.rs.core.MultivaluedMap,
     *      java.io.InputStream)
     */
    public abstract T readFrom(Class<T> type, Type genericType,
            MediaType mediaType, Annotation[] annotations,
            MultivaluedMap<String, String> httpResponseHeaders,
            InputStream entityStream) throws IOException;

    /**
     * Returns the class object supported by this provider.
     * 
     * @return the class object supported by this provider.
     */
    protected Class<?> supportedClass() {
        throw new UnsupportedOperationException(
                "You must implement method "
                        + this.getClass().getName()
                        + ".supportedClass(), if you do not implement isReadable(...) or isWriteable(...)");
    }

    /**
     * @see MessageBodyWriter#writeTo(Object, Class, Type, Annotation[],
     *      MediaType, MultivaluedMap, OutputStream)
     */
    public abstract void writeTo(T object, Class<?> type, Type genericType,
            Annotation[] annotations, MediaType mediaType,
            MultivaluedMap<String, Object> httpHeaders,
            OutputStream entityStream) throws IOException;
}