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
package org.restlet.ext.jaxrs.internal.wrappers;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.ws.rs.CookieParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.Encoded;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.restlet.data.ClientInfo;
import org.restlet.data.Conditions;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Parameter;
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.ext.jaxrs.JaxRsRouter;
import org.restlet.ext.jaxrs.internal.core.CallContext;
import org.restlet.ext.jaxrs.internal.exceptions.ConvertCookieParamException;
import org.restlet.ext.jaxrs.internal.exceptions.ConvertHeaderParamException;
import org.restlet.ext.jaxrs.internal.exceptions.ConvertMatrixParamException;
import org.restlet.ext.jaxrs.internal.exceptions.ConvertParameterException;
import org.restlet.ext.jaxrs.internal.exceptions.ConvertPathParamException;
import org.restlet.ext.jaxrs.internal.exceptions.ConvertQueryParamException;
import org.restlet.ext.jaxrs.internal.exceptions.ConvertRepresentationException;
import org.restlet.ext.jaxrs.internal.exceptions.IllegalTypeException;
import org.restlet.ext.jaxrs.internal.exceptions.InstantiateException;
import org.restlet.ext.jaxrs.internal.exceptions.MissingAnnotationException;
import org.restlet.ext.jaxrs.internal.exceptions.NoMessageBodyReaderException;
import org.restlet.ext.jaxrs.internal.util.Converter;
import org.restlet.ext.jaxrs.internal.util.Util;
import org.restlet.resource.Representation;
import org.restlet.util.Series;

/**
 * Utility methods for the wrappers.
 * 
 * @author Stephan Koops
 */
public class WrapperUtil {

    static class ParamValueIter implements Iterator<String> {
        private Iterator<Parameter> paramIter;

        ParamValueIter(Iterable<Parameter> parameters) {
            this.paramIter = parameters.iterator();
        }

        /** @see java.util.Iterator#hasNext() */
        public boolean hasNext() {
            return paramIter.hasNext();
        }

        /** @see java.util.Iterator#next() */
        public String next() {
            return getValue(paramIter.next());
        }

        /** @see java.util.Iterator#remove() */
        public void remove() {
            paramIter.remove();
        }
    }

    private static final String COLL_PARAM_NOT_DEFAULT = "The collection type Collection is not supported for parameters. Use List, Set or SortedSet";

    private static final Boolean DEFAULT_BOOLEAN = Boolean.FALSE;

    private static final Byte DEFAULT_BYTE = (byte) 0;

    private static final Character DEFAULT_CHAR = new Character('\0');

    private static final Double DEFAULT_DOUBLE = 0d;

    private static final Float DEFAULT_FLOAT = 0.0f;

    private static final Integer DEFAULT_INT = 0;

    private static final Long DEFAULT_LONG = new Long(0);

    private static final Short DEFAULT_SHORT = 0;

    private static final String JAX_RS_PACKAGE_PREFIX = "javax.ws.rs";

    private static final Collection<Class<? extends Annotation>> VALID_ANNOTATIONS = createValidAnnotations();

    /**
     * 
     * @param javaMethod
     *                Java method, class or something like that.
     * @return true, if the given accessible object is annotated with any
     *         JAX-RS-Annotation.
     */
    static boolean checkForJaxRsAnnotations(Method javaMethod) {
        for (Annotation annotation : javaMethod.getAnnotations()) {
            Class<? extends Annotation> annoType = annotation.annotationType();
            if (annoType.getName().startsWith(WrapperUtil.JAX_RS_PACKAGE_PREFIX))
                return true;
            if (annoType.isAnnotationPresent(HttpMethod.class))
                return true;
        }
        return false;
    }

    /**
     * Checks if the parameters for the constructor are valid for a JAX-RS root
     * resource class.
     * 
     * @param paramAnnotationss
     * @param parameterTypes
     * @returns true, if the
     * @throws IllegalTypeException
     *                 If a parameter is annotated with {@link Context}, but
     *                 the type is invalid (must be UriInfo, Request or
     *                 HttpHeaders).
     */
    private static boolean checkParamAnnotations(Constructor<?> constr) {
        Annotation[][] paramAnnotationss = constr.getParameterAnnotations();
        Class<?>[] parameterTypes = constr.getParameterTypes();
        for (int i = 0; i < paramAnnotationss.length; i++) {
            Annotation[] parameterAnnotations = paramAnnotationss[i];
            Class<?> parameterType = parameterTypes[i];
            boolean ok = checkParameterAnnotation(parameterAnnotations,
                    parameterType);
            if (!ok)
                return false;
        }
        return true;
    }

    /**
     * Checks, if the annotations are valid for a runtime environment handled
     * constructor.
     * 
     * @param parameterAnnotations
     * @param parameterType
     * @return
     */
    private static boolean checkParameterAnnotation(
            Annotation[] parameterAnnotations, Class<?> parameterType) {
        if (parameterAnnotations.length == 0)
            return false;
        for (Annotation annotation : parameterAnnotations) {
            Class<? extends Annotation> annotationType = annotation
                    .annotationType();
            if (annotationType.equals(HeaderParam.class)) {
                continue;
            } else if (annotationType.equals(PathParam.class)) {
                continue;
            } else if (annotationType.equals(Context.class)) {
                if (parameterType.equals(UriInfo.class))
                    continue;
                if (parameterType.equals(Request.class))
                    continue;
                if (parameterType.equals(HttpHeaders.class))
                    continue;
                if (parameterType.equals(SecurityContext.class))
                    continue;
                return false;
            } else if (annotationType.equals(MatrixParam.class)) {
                continue;
            } else if (annotationType.equals(QueryParam.class)) {
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * converts the given value without any decoding.
     * 
     * @param paramClass
     * @param paramValue
     * @param defaultValue
     * @return
     * @throws ConvertParameterException
     */
    private static Object convertParamValueFromParam(Class<?> paramClass,
            String paramValue, DefaultValue defaultValue)
            throws ConvertParameterException {
        try {
            Constructor<?> constr = paramClass.getConstructor(String.class);
            return constr.newInstance(paramValue);
        } catch (Exception e) {
            // try valueOf(String) as next step
        }
        Method valueOf;
        try {
            valueOf = paramClass.getMethod("valueOf", String.class);
        } catch (SecurityException e) {
            throw ConvertParameterException.object(paramClass, paramValue, e);
        } catch (NoSuchMethodException e) {
            throw ConvertParameterException.object(paramClass, paramValue, e);
        }
        try {
            return valueOf.invoke(null, paramValue);
        } catch (IllegalArgumentException e) {
            throw ConvertParameterException.object(paramClass, paramValue, e);
        } catch (IllegalAccessException e) {
            throw ConvertParameterException.object(paramClass, paramValue, e);
        } catch (InvocationTargetException e) {
            if ((paramValue == null || paramValue.length() <= 0)
                    && (e.getCause() instanceof IllegalArgumentException)) {
                if (defaultValue == null)
                    return null;
                else {
                    String dfv = defaultValue.value();
                    return convertParamValueFromParam(paramClass, dfv, null);
                }
            }
            throw ConvertParameterException.object(paramClass, paramValue, e);
        }
    }

    /**
     * Converts the given paramValue (found in the path, query, matrix or
     * header) into the given paramClass.
     * 
     * @param paramClass
     *                the type of the parameter to convert to
     * @param paramValue
     * @param defaultValue
     *                see {@link DefaultValue}
     * @param leaveEncoded
     *                if true, leave {@link QueryParam}s, {@link MatrixParam}s
     *                and {@link PathParam}s encoded. Must be FALSE for
     *                {@link HeaderParam}s.
     * @param jaxRsRouter
     * @return
     * @throws ConvertParameterException
     * @see PathParam
     * @see MatrixParam
     * @see QueryParam
     * @see HeaderParam
     */
    private static Object convertParamValueFromParam(Class<?> paramClass,
            String paramValue, DefaultValue defaultValue, boolean leaveEncoded)
            throws ConvertParameterException {
        if (!leaveEncoded && paramValue != null)
            paramValue = Reference.decode(paramValue);
        else if (paramValue == null && defaultValue != null)
            paramValue = defaultValue.value();
        if (paramClass.equals(String.class)) // optimization
            return paramValue;
        if (paramClass.isPrimitive()) {
            if (paramValue != null && paramValue.length() <= 0)
                paramValue = defaultValue.value();
            return getParamValueForPrimitive(paramClass, paramValue);
        }
        return convertParamValueFromParam(paramClass, paramValue, defaultValue);
    }

    /**
     * @param paramClass
     * @param paramGenericType
     * @param paramValueIter
     *                the values to use if multiples are required
     * @param paramValue
     *                the value, if only one is needed.
     * @param defaultValue
     * @param leaveEncoded
     * @return
     * @throws ConvertParameterException
     */
    private static Object convertParamValuesFromParam(Class<?> paramClass,
            Type paramGenericType, Iterator<String> paramValueIter,
            String paramValue, DefaultValue defaultValue, boolean leaveEncoded)
            throws ConvertParameterException {
        boolean toArray = false;
        Collection<Object> coll = null;
        if (paramClass.isArray()) {
            coll = new ArrayList<Object>(1);
            toArray = true;
            paramClass = paramClass.getComponentType();
        } else if (paramGenericType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) paramGenericType;
            coll = createColl(parameterizedType);
            paramClass = (Class<?>) parameterizedType.getActualTypeArguments()[0];
        }
        if (coll == null) { // no collection type
            return convertParamValueFromParam(paramClass, paramValue,
                    defaultValue, leaveEncoded);
        }
        while (paramValueIter.hasNext()) {
            String queryParamValue = paramValueIter.next();
            Object convertedValue = convertParamValueFromParam(paramClass,
                    queryParamValue, defaultValue, leaveEncoded);
            if (convertedValue != null)
                coll.add(convertedValue);
            defaultValue = null;
        }
        if (coll.isEmpty()) // add default value
            coll.add(convertParamValueFromParam(paramClass, paramValue,
                    defaultValue, leaveEncoded));
        if (toArray)
            return Util.toArray(coll, paramClass);
        return coll;
    }

    /**
     * Converts the Restlet request {@link Representation} to the type requested
     * by the resource method.
     * 
     * @param callContext
     *                the call context, containing the entity.
     * @param paramType
     *                the type to convert to.
     * @param genericType
     *                The generic {@link Type} to convert to.
     * @param annotations
     *                the annotations of the artefact to convert to
     * @param mbrs
     *                The Set of all available {@link MessageBodyReader}s in
     *                the {@link JaxRsRouter}.
     * @param logger
     *                The logger to use
     * @return
     * @throws NoMessageBodyReaderException
     * @throws ConvertRepresentationException
     */
    @SuppressWarnings("unchecked")
    static Object convertRepresentation(CallContext callContext,
            Class<?> paramType, Type genericType, Annotation[] annotations,
            MessageBodyReaderSet mbrs, Logger logger)
            throws NoMessageBodyReaderException, ConvertRepresentationException {
        Representation entity = callContext.getRequest().getEntity();
        if (entity == null)
            return null;
        if (Representation.class.isAssignableFrom(paramType)) {
            Object repr = createConcreteRepresentationInstance(paramType,
                    entity, logger);
            if (repr != null)
                return repr;
        }
        MediaType mediaType = entity.getMediaType();
        MessageBodyReader<?> mbr = mbrs.getBest(mediaType, paramType,
                genericType, annotations);
        if (mbr == null)
            throw new NoMessageBodyReaderException(mediaType, paramType);
        MultivaluedMap<String, String> httpHeaders = Util
                .getJaxRsHttpHeaders(callContext.getRequest());
        try {
            javax.ws.rs.core.MediaType jaxRsMediaType = Converter
                    .toJaxRsMediaType(mediaType, entity.getCharacterSet());
            return mbr.readFrom((Class) paramType, genericType, jaxRsMediaType,
                    annotations, httpHeaders, entity.getStream());
        } catch (IOException e) {
            throw ConvertRepresentationException.object(paramType,
                    "the message body", e);
        }
    }

    /**
     * Converts the given mimes to a List of MediaTypes. Will never returns
     * null.
     * 
     * @param mimes
     * @return Returns an unmodifiable List of MediaTypes
     */
    static List<MediaType> convertToMediaTypes(String[] mimes) {
        List<MediaType> mediaTypes = new ArrayList<MediaType>(mimes.length);
        for (String mime : mimes) {
            if (mime == null)
                mediaTypes.add(MediaType.ALL);
            else
                mediaTypes.add(MediaType.valueOf(mime));
        }
        return Collections.unmodifiableList(mediaTypes);
    }

    /**
     * @param type
     * @return
     */
    private static <A> Collection<A> createColl(ParameterizedType type) {
        Type rawType = type.getRawType();
        if (rawType.equals(List.class))
            return new ArrayList<A>(1);
        else if (rawType.equals(Set.class))
            return new HashSet<A>(2);
        else if (rawType.equals(SortedSet.class))
            return new TreeSet<A>();
        else if (rawType.equals(Collection.class)) {
            Logger logger = Logger.getAnonymousLogger();
            logger.config(WrapperUtil.COLL_PARAM_NOT_DEFAULT);
            return new ArrayList<A>();
        }
        return null;
    }

    /**
     * @param entity
     * @return the created representation, or null, if it could not be
     *         converted.
     * @throws ConvertParameterException
     */
    private static Object createConcreteRepresentationInstance(
            Class<?> paramType, Representation entity, Logger logger)
            throws ConvertRepresentationException {
        if (paramType.equals(Representation.class))
            return entity;
        Constructor<?> constr;
        try {
            constr = paramType.getConstructor(Representation.class);
        } catch (SecurityException e) {
            logger.warning("The constructor " + paramType
                    + "(Representation) is not accessable.");
            return null;
        } catch (NoSuchMethodException e) {
            return null;
        }
        try {
            return constr.newInstance(entity);
        } catch (Exception e) {
            throw ConvertRepresentationException.object(paramType,
                    "the message body", e);
        }
    }

    /**
     * Creates an instance of the root resource class.
     * 
     * @param constructor
     *                the constructor to create an instance with.
     * @param leaveEncoded
     *                if true, leave {@link QueryParam}s, {@link MatrixParam}s
     *                and {@link PathParam}s encoded.
     * @param callContext
     *                Contains the encoded template Parameters, that are read
     *                from the called URI, the Restlet {@link Request} and the
     *                Restlet {@link Response}.
     * @param mbrs
     *                The Set of all available {@link MessageBodyReader}s in
     *                the {@link JaxRsRouter}.
     * @param logger
     *                The logger to use
     * @return
     * @throws MissingAnnotationException
     * @throws NoMessageBodyReaderException
     * @throws InstantiateException
     *                 if the class could not be instantiated.
     * @throws InvocationTargetException
     * @throws ConvertCookieParamException
     * @throws ConvertQueryParamException
     * @throws ConvertMatrixParamException
     * @throws ConvertPathParamException
     * @throws ConvertHeaderParamException
     * @throws ConvertRepresentationException
     */
    static Object createInstance(Constructor<?> constructor,
            boolean leaveEncoded, CallContext callContext,
            MessageBodyReaderSet mbrs, Logger logger)
            throws MissingAnnotationException, NoMessageBodyReaderException,
            InstantiateException, InvocationTargetException,
            ConvertRepresentationException, ConvertHeaderParamException,
            ConvertPathParamException, ConvertMatrixParamException,
            ConvertQueryParamException, ConvertCookieParamException {
        // TESTEN what happens if @*Param on Provider constructor.
        Object[] args;
        if (constructor.getParameterTypes().length == 0) {
            args = new Object[0];
        } else {
            args = getParameterValues(constructor.getParameterTypes(),
                    constructor.getGenericParameterTypes(), constructor
                            .getParameterAnnotations(), leaveEncoded,
                    callContext, mbrs, logger);
        }
        try {
            return constructor.newInstance(args);
        } catch (IllegalArgumentException e) {
            throw new InstantiateException(
                    "Could not instantiate " + constructor.getDeclaringClass(),
                    e);
        } catch (InstantiationException e) {
            throw new InstantiateException(
                    "Could not instantiate " + constructor.getDeclaringClass(),
                    e);
        } catch (IllegalAccessException e) {
            throw new InstantiateException(
                    "Could not instantiate " + constructor.getDeclaringClass(),
                    e);
        }
    }

    @SuppressWarnings("unchecked")
    static Collection<Class<? extends Annotation>> createValidAnnotations() {
        return Arrays.asList(Context.class, HeaderParam.class,
                MatrixParam.class, QueryParam.class, PathParam.class,
                CookieParam.class);
    }

    /**
     * @param jaxRsClass
     * @return Returns the constructor to use for the given root resource class
     *         (See JSR-311-Spec, section 2.3). If no constructor could be
     *         found, null is returned. Than try {@link Class#newInstance()}
     * @throws IllegalTypeException
     */
    static Constructor<?> findJaxRsConstructor(Class<?> jaxRsClass) {
        Constructor<?> constructor = null;
        int constructorParamNo = Integer.MIN_VALUE;
        for (Constructor<?> constr : jaxRsClass.getConstructors()) {
            if (!Modifier.isPublic(constr.getModifiers()))
                continue;
            int constrParamNo = constr.getParameterTypes().length;
            if (constrParamNo <= constructorParamNo)
                continue; // ignore this constructor
            if (!checkParamAnnotations(constr))
                continue; // ignore this constructor
            constructor = constr;
            constructorParamNo = constrParamNo;
        }
        return constructor;
    }

    /**
     * Creates the {@link ContextResolver} to inject in the given field.
     * 
     * @param field
     * @param allResolvers
     * @return
     */
    @SuppressWarnings("unchecked")
    static javax.ws.rs.ext.ContextResolver<?> getContextResolver(Field field,
            Collection<ContextResolver<?>> allResolvers) {
        Type genType = field.getGenericType();
        if (!(genType instanceof ParameterizedType))
            return ReturnNullContextResolver.get();
        Type t = ((ParameterizedType) genType).getActualTypeArguments()[0];
        if (!(t instanceof Class))
            return ReturnNullContextResolver.get();
        Class crType = (Class) t;
        List<javax.ws.rs.ext.ContextResolver<?>> returnResolvers = new ArrayList<javax.ws.rs.ext.ContextResolver<?>>();
        for (ContextResolver<?> cr : allResolvers) {
            javax.ws.rs.ext.ContextResolver<?> jaxRsResolver;
            jaxRsResolver = cr.getJaxRsContextResolver();
            Class<?> crClaz = jaxRsResolver.getClass();
            try {
                Method getContext = crClaz.getMethod("getContext", Class.class);
                if (getContext.getReturnType().equals(crType))
                    returnResolvers.add(jaxRsResolver);
            } catch (SecurityException e) {
                throw new RuntimeException(
                        "sorry, the method getContext(Class) of ContextResolver "
                                + crClaz + " is not accessible");
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(
                        "The ContextResolver "
                                + crClaz
                                + " is not valid, because it has no method getContext(Class)");
            }
        }
        if (returnResolvers.isEmpty())
            return ReturnNullContextResolver.get();
        if (returnResolvers.size() == 1)
            return returnResolvers.get(0);
        return new ContextResolverCollection(returnResolvers);
    }

    /**
     * @param paramClass
     *                the class to convert to
     * @param paramGenericType
     *                the generic type to convert to
     * @param cookieParam
     *                the {@link CookieParam} annotation
     * @param defaultValue
     *                the default value
     * @param callContext
     *                the {@link CallContext}
     * @param jaxRsRouter
     * @return the cookie parameter, converted to type paramClass
     * @throws ConvertCookieParamException
     */
    @SuppressWarnings("unchecked")
    static Object getCookieParamValue(Class<?> paramClass,
            Type paramGenericType, CookieParam cookieParam,
            DefaultValue defaultValue, CallContext callContext)
            throws ConvertCookieParamException {
        String cookieName = cookieParam.value();
        Collection<Cookie> coll = null;
        boolean isCookie = false; // javax.ws.rs.core.Cookie requested
        boolean toArray = false;
        if (paramClass.equals(Cookie.class)) {
            isCookie = true;
        } else if (paramClass.isArray()) {
            Class<?> paramClass2 = paramClass.getComponentType();
            if (paramClass2.equals(Cookie.class)) {
                coll = new ArrayList<Cookie>();
                toArray = true;
                isCookie = true;
            }
        } else if (paramGenericType instanceof ParameterizedType) {
            ParameterizedType parametrizedType = (ParameterizedType) paramGenericType;
            Type[] argTypes = parametrizedType.getActualTypeArguments();
            if (argTypes.length == 1 && argTypes[0].equals(Cookie.class)) {
                coll = createColl(parametrizedType);
                if (coll != null)
                    isCookie = true;
            }
        }
        Series<org.restlet.data.Cookie> cookies;
        cookies = callContext.getRequest().getCookies();
        if (isCookie) {
            for (org.restlet.data.Cookie rc : cookies) {
                if (!rc.getName().equals(cookieName))
                    continue;
                Cookie cookie = Converter.toJaxRsCookie(rc);
                if (coll == null) // no collection requested
                    return cookie;
                coll.add(cookie);
            }
            if (coll == null)
                return null;
            if (coll.isEmpty()) {
                String value = defaultValue.value();
                coll.add(new Cookie(cookieName, value));
            }
            if (toArray)
                return Util.toArray(coll, Cookie.class);
            return coll;
        }
        try {
            return convertParamValuesFromParam(paramClass, paramGenericType,
                    new ParamValueIter((Series) cookies.subList(cookieName)),
                    getValue(cookies.getFirst(cookieName)), defaultValue, true);
        } catch (ConvertParameterException e) {
            throw new ConvertCookieParamException(e);
        }
        // leaveEncoded = true -> not change
    }

    /**
     * @param paramClass
     * @param paramGenericType
     *                the generic type to convert to
     * @param annotation
     * @param defaultValue
     * @param callContext
     * @param jaxRsRouter
     * @return
     * @throws ConvertHeaderParamException
     */
    static Object getHeaderParamValue(Class<?> paramClass,
            Type paramGenericType, HeaderParam annotation,
            DefaultValue defaultValue, CallContext callContext)
            throws ConvertHeaderParamException {
        Form httpHeaders = Util.getHttpHeaders(callContext.getRequest());
        String headerName = annotation.value();
        try {
            return convertParamValuesFromParam(paramClass, paramGenericType,
                    new ParamValueIter(httpHeaders.subList(headerName, true)),
                    getValue(httpHeaders.getFirst(headerName, true)),
                    defaultValue, true);
        } catch (ConvertParameterException e) {
            throw new ConvertHeaderParamException(e);
        }
    }

    static org.restlet.data.Method getHttpMethod(Method javaMethod) {
        for (Annotation annotation : javaMethod.getAnnotations()) {
            Class<? extends Annotation> annoType = annotation.annotationType();
            HttpMethod httpMethodAnnot = annoType
                    .getAnnotation(HttpMethod.class);
            if (httpMethodAnnot != null) {
                // Annotation of Annotation of the method is the HTTP-Method
                String httpMethodName = httpMethodAnnot.value();
                return org.restlet.data.Method.valueOf(httpMethodName);
            }
        }
        return null;
    }

    /**
     * @param paramClass
     * @param paramGenericType
     *                the generic type to convert to
     * @param matrixParam
     * @param leaveEncoded
     * @param defaultValue
     * @param callContext
     * @param jaxRsRouter
     * @return
     * @throws ConvertMatrixParamException
     */
    static Object getMatrixParamValue(Class<?> paramClass,
            Type paramGenericType, MatrixParam matrixParam,
            boolean leaveEncoded, DefaultValue defaultValue,
            CallContext callContext) throws ConvertMatrixParamException {
        String matrixParamValue = callContext
                .getLastMatrixParamEnc(matrixParam);
        Iterator<String> matrixParamValues = callContext
                .matrixParamEncIter(matrixParam);
        try {
            return convertParamValuesFromParam(paramClass, paramGenericType,
                    matrixParamValues, matrixParamValue, defaultValue,
                    leaveEncoded);
        } catch (ConvertParameterException e) {
            throw new ConvertMatrixParamException(e);
        }
    }

    /**
     * Returns the parameter value for a parameter of a JAX-RS method or
     * constructor.
     * 
     * @param paramAnnotations
     *                annotations on the paramIter
     * @param paramClass
     *                the wished type
     * @param genericParamType
     *                the generic type to convert to
     * @param callContext
     *                Contains the encoded template Parameters, that are read
     *                from the called URI, the Restlet {@link Request} and the
     *                Restlet {@link Response}.
     * @param leaveEncoded
     *                if true, leave {@link QueryParam}s, {@link MatrixParam}s
     *                and {@link PathParam}s encoded.
     * @param indexForExcMessages
     *                the index of the parameter, for exception messages.
     * @param jaxRsRouter
     * @return the parameter value
     * @throws MissingAnnotationException
     *                 Thrown, when no valid annotation was found. For
     *                 (Sub)ResourceMethods this is one times allowed; than the
     *                 given request entity should taken as parameter.
     * @throws ConvertHeaderParamException
     * @throws ConvertPathParamException
     * @throws ConvertMatrixParamException
     * @throws ConvertQueryParamException
     * @throws ConvertCookieParamException
     */
    private static Object getParameterValue(Annotation[] paramAnnotations,
            Class<?> paramClass, Type paramGenericType,
            CallContext callContext, Logger logger, boolean leaveEncoded,
            int indexForExcMessages) throws MissingAnnotationException,
            ConvertHeaderParamException, ConvertPathParamException,
            ConvertMatrixParamException, ConvertQueryParamException,
            ConvertCookieParamException {
        DefaultValue defaultValue = null;
        for (Annotation annot : paramAnnotations) {
            Class<? extends Annotation> annotationType = annot.annotationType();
            if (annotationType.equals(DefaultValue.class))
                defaultValue = (DefaultValue) annot;
            else if (!leaveEncoded && annotationType.equals(Encoded.class))
                leaveEncoded = true;
        }
        for (Annotation annotation : paramAnnotations) {
            Class<? extends Annotation> annoType = annotation.annotationType();
            if (annoType.equals(Context.class)) {
                if (paramClass.equals(ClientInfo.class))
                    return callContext.getRequest().getClientInfo();
                else if (paramClass.equals(Conditions.class))
                    return callContext.getRequest().getConditions();
                else
                    return callContext;
            }
            if (annoType.equals(HeaderParam.class)) {
                return getHeaderParamValue(paramClass, paramGenericType,
                        (HeaderParam) annotation, defaultValue, callContext);
            }
            if (annoType.equals(PathParam.class)) {
                return getPathParamValue(paramClass, paramGenericType,
                        (PathParam) annotation, leaveEncoded, defaultValue,
                        callContext);
            }
            if (annoType.equals(MatrixParam.class)) {
                return getMatrixParamValue(paramClass, paramGenericType,
                        (MatrixParam) annotation, leaveEncoded, defaultValue,
                        callContext);
            }
            if (annoType.equals(QueryParam.class)) {
                return getQueryParamValue(paramClass, paramGenericType,
                        (QueryParam) annotation, leaveEncoded, defaultValue,
                        callContext, logger);
            }
            if (annoType.equals(CookieParam.class)) {
                return getCookieParamValue(paramClass, paramGenericType,
                        (CookieParam) annotation, defaultValue, callContext);
            }
        }
        throw new MissingAnnotationException("The " + indexForExcMessages
                + ". parameter requires one of the following annotations: "
                + VALID_ANNOTATIONS);
    }

    /**
     * Returns the parameter value array for a JAX-RS method or constructor.
     * 
     * @param paramTypes
     *                the array of types for the method or constructor.
     * @param paramGenericTypes
     *                The generic {@link Type} to convert to.
     * @param paramAnnotationss
     *                the array of arrays of annotations for the method or
     *                constructor.
     * @param leaveEncoded
     *                if true, leave {@link QueryParam}s, {@link MatrixParam}s
     *                and {@link PathParam}s encoded.
     * @param callContext
     *                Contains the encoded template Parameters, that are read
     *                from the called URI, the Restlet {@link Request} and the
     *                Restlet {@link Response}.
     * @param mbrs
     *                The Set of all available {@link MessageBodyReader}s in
     *                the {@link JaxRsRouter}.
     * @param logger
     *                The logger to use
     * @return the parameter array
     * @throws MissingAnnotationException
     * @throws NoMessageBodyReaderException
     * @throws ConvertCookieParamException
     * @throws ConvertQueryParamException
     * @throws ConvertMatrixParamException
     * @throws ConvertPathParamException
     * @throws ConvertHeaderParamException
     * @throws ConvertRepresentationException
     */
    static Object[] getParameterValues(Class<?>[] paramTypes,
            Type[] paramGenericTypes, Annotation[][] paramAnnotationss,
            boolean leaveEncoded, CallContext callContext,
            MessageBodyReaderSet mbrs, Logger logger)
            throws MissingAnnotationException, NoMessageBodyReaderException,
            ConvertHeaderParamException, ConvertPathParamException,
            ConvertMatrixParamException, ConvertQueryParamException,
            ConvertCookieParamException, ConvertRepresentationException {
        int paramNo = paramTypes.length;
        if (paramNo == 0)
            return new Object[0];
        Object[] args = new Object[paramNo];
        boolean annotRequired = false;
        if (logger == null)
            logger = Logger.getAnonymousLogger();
        for (int i = 0; i < args.length; i++) {
            Class<?> paramType = paramTypes[i];
            Type paramGenericType = paramGenericTypes[i];
            Object arg;
            Annotation[] paramAnnotations = paramAnnotationss[i];
            try {
                arg = getParameterValue(paramAnnotations, paramType,
                        paramGenericType, callContext, logger, leaveEncoded, i);
            } catch (MissingAnnotationException ionae) {
                if (annotRequired)
                    throw ionae;
                annotRequired = true;
                // TODO check, if the result could be read multiple (i.e. in
                // some sub resource locators and again in the resource method
                // or double in the resource method.
                // if yes, save converted Representation to CallContext,
                // (not possible e.g. for InputStream and Reader)
                arg = convertRepresentation(callContext, paramType,
                        paramGenericType, paramAnnotations, mbrs, logger);
                // TODO convert representation before first access, because
                // MessageBodyReader may change headers.
                // This is another argument against changing the headers in an
                // EntityProvider. If the entity is not needed (e.g.
                // Precondition failed), the MessageBodyReader s not required to
                // be called and does not need time for the conversion
                // TODO ensure Representation.release().
                // REQUESTED can we add a method if a MessageBodyReader and/or
                // writer will possibly change the headers:
                // enum MessageBodyReader.Header.CHANGED, READ, NOT_NEEDED
                // if it is not needed, null could be given for the headers
                // instead of a Map.
                // Same for MessageBodyWriter: Headers could be written before
                // writeTo starts: The headers could be sent to client, while
                // JAX-RS runtime environment is working between header writing
                // and body writing.
                // REQUESTED Request.getEntity: converts entity only if needed.
                // If the entity is not required (e.g. prec failed) it could be
                // not converted, if no headers are needed. (The last is the
                // case in the most cases, I think.)
            }
            args[i] = arg;
        }
        return args;
    }

    /**
     * @param paramClass
     * @param paramValue
     * @throws ConvertParameterException
     */
    private static Object getParamValueForPrimitive(Class<?> paramClass,
            String paramValue) throws ConvertParameterException {
        try {
            if (paramClass == Integer.TYPE) {
                if ((paramValue == null || paramValue.length() <= 0))
                    return DEFAULT_INT;
                return new Integer(paramValue);
            }
            if (paramClass == Double.TYPE) {
                if ((paramValue == null || paramValue.length() <= 0))
                    return DEFAULT_DOUBLE;
                return new Double(paramValue);
            }
            if (paramClass == Float.TYPE) {
                if ((paramValue == null || paramValue.length() <= 0))
                    return DEFAULT_FLOAT;
                return new Float(paramValue);
            }
            if (paramClass == Byte.TYPE) {
                if ((paramValue == null || paramValue.length() <= 0))
                    return DEFAULT_BYTE;
                return new Byte(paramValue);
            }
            if (paramClass == Long.TYPE) {
                if ((paramValue == null || paramValue.length() <= 0))
                    return DEFAULT_LONG;
                return new Long(paramValue);
            }
            if (paramClass == Short.TYPE) {
                if ((paramValue == null || paramValue.length() <= 0))
                    return DEFAULT_SHORT;
                return new Short(paramValue);
            }
            if (paramClass == Character.TYPE) {
                if ((paramValue == null || paramValue.length() <= 0))
                    return DEFAULT_CHAR;
                if (paramValue.length() == 1)
                    return paramValue.charAt(0);
                throw ConvertParameterException.primitive(paramClass,
                        paramValue, null);
            }
            if (paramClass == Boolean.TYPE) {
                if ((paramValue == null || paramValue.length() <= 0))
                    return DEFAULT_BOOLEAN;
                if (paramValue.equalsIgnoreCase("true"))
                    return Boolean.TRUE;
                if (paramValue.equalsIgnoreCase("false"))
                    return Boolean.FALSE;
                throw ConvertParameterException.primitive(paramClass,
                        paramValue, null);
            }
        } catch (IllegalArgumentException e) {
            throw ConvertParameterException
                    .primitive(paramClass, paramValue, e);
        }
        String warning;
        if (paramClass == Void.TYPE)
            warning = "an object should be converted to a void; but this could not be here";
        else
            warning = "an object should be converted to a " + paramClass
                    + ", but here are only primitives allowed.";
        Logger.getAnonymousLogger().warning(warning);
        ResponseBuilder rb = javax.ws.rs.core.Response.serverError();
        rb.entity(warning);
        throw new WebApplicationException(rb.build());
    }

    /**
     * @param paramClass
     * @param paramGenericType
     *                the generic type to convert to
     * @param pathParam
     * @param leaveEncoded
     * @param defaultValue
     * @param callContext
     * @param logger
     * @return
     * @throws ConvertPathParamException
     */
    static Object getPathParamValue(Class<?> paramClass, Type paramGenericType,
            PathParam pathParam, boolean leaveEncoded,
            DefaultValue defaultValue, CallContext callContext)
            throws ConvertPathParamException {
        // LATER testen Path-Param: List<String> (see PathParamTest.testGet3())
        // TODO @PathParam("x") PathSegment allowed.

        String pathParamValue = callContext.getLastPathParamEnc(pathParam);
        Iterator<String> pathParamValueIter = callContext
                .pathParamEncIter(pathParam);
        try {
            return convertParamValuesFromParam(paramClass, paramGenericType,
                    pathParamValueIter, pathParamValue, defaultValue,
                    leaveEncoded);
        } catch (ConvertParameterException e) {
            throw new ConvertPathParamException(e);
        }
    }

    /**
     * @param paramClass
     * @param paramGenericType
     *                the generic type to convert to
     * @param queryParam
     * @param leaveEncoded
     * @param defaultValue
     * @param callContext
     * @param logger
     * @return
     * @throws ConvertQueryParamException
     */
    static Object getQueryParamValue(Class<?> paramClass,
            Type paramGenericType, QueryParam queryParam, boolean leaveEncoded,
            DefaultValue defaultValue, CallContext callContext, Logger logger)
            throws ConvertQueryParamException {
        Reference resourceRef = callContext.getRequest().getResourceRef();
        String queryString = resourceRef.getQuery();
        Form form = Converter.toFormEncoded(queryString, logger);
        String paramName = queryParam.value();
        List<Parameter> parameters = form.subList(paramName);
        try {
            String queryParamValue = getValue(form.getFirst(paramName));
            ParamValueIter queryParamValueIter = new ParamValueIter(parameters);
            return convertParamValuesFromParam(paramClass, paramGenericType,
                    queryParamValueIter, queryParamValue, defaultValue,
                    leaveEncoded);
        } catch (ConvertParameterException e) {
            throw new ConvertQueryParamException(e);
        }
    }

    /**
     * Returns the value from the given Parameter. If the given parameter is
     * null, null will returned. If the parameter is not null, but it's value, ""
     * is returned.
     * 
     * @param parameter
     * @return the value from the given Parameter. If the given parameter is
     *         null, null will returned. If the parameter is not null, but it's
     *         value, "" is returned.
     */
    private static String getValue(Parameter parameter) {
        if (parameter == null)
            return null;
        String paramValue = parameter.getValue();
        if (paramValue == null)
            return "";
        return paramValue;
    }
}