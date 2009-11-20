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

package org.restlet.ext.jaxrs.wrappers;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import javax.ws.rs.Encoded;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;

import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.ext.jaxrs.core.CallContext;
import org.restlet.ext.jaxrs.exceptions.MissingAnnotationException;
import org.restlet.ext.jaxrs.exceptions.InstantiateParameterException;
import org.restlet.ext.jaxrs.exceptions.MethodInvokeException;
import org.restlet.ext.jaxrs.exceptions.NoMessageBodyReadersException;
import org.restlet.ext.jaxrs.exceptions.RequestHandledException;
import org.restlet.ext.jaxrs.util.Util;

/**
 * An abstract wrapper class. Contains some a static methods to use from
 * everywhere, otherwise not intended for public use
 * 
 * @author Stephan Koops
 */
public abstract class AbstractMethodWrapper extends AbstractJaxRsWrapper {

    /**
     * Returns the path template of the given sub resource locator or sub
     * resource method.
     * 
     * @param javaMethod
     *                the java method
     * @return the path template or null, if no path template is available (than
     *         the method is not a sub resource locator or sub resource method.)
     */
    public static String getPathTemplate(Method javaMethod) {
        Path path = javaMethod.getAnnotation(Path.class);
        if (path == null)
            return null;
        return AbstractJaxRsWrapper.getPathTemplate(path);
    }

    /**
     * is true, if the wrapped java method or its class is annotated with
     * &#64;Path.
     */
    boolean leaveEncoded;

    /**
     * the Java method that should be called. This method could be different
     * from the methods containing the annotations, see section 2.5 "Annotation
     * Inheritance" of JSR-311-spec.
     */
    Method javaMethod;

    ResourceClass resourceClass;

    AbstractMethodWrapper(Method javaMethod, Path path,
            ResourceClass resourceClass) {
        super(path);
        this.javaMethod = javaMethod;
        this.resourceClass = resourceClass;
        if (resourceClass.leaveEncoded
                || javaMethod.isAnnotationPresent(Encoded.class))
            this.leaveEncoded = true;
        else
            this.leaveEncoded = false;
    }

    /**
     * Invokes the method and returned the created representation for the
     * response.
     * 
     * @param resourceObject
     * @param jaxRsRouter
     * @param callContext
     *                Contains the encoded template Parameters, that are read
     *                from the called URI, the Restlet {@link Request} and the
     *                Restlet {@link Response}.
     * @return
     * @throws MethodInvokeException
     * @throws InvocationTargetException
     * @throws InstantiateParameterException
     * @throws NoMessageBodyReadersException
     * @throws RequestHandledException
     * @throws WebApplicationException
     * @throws MissingAnnotationException
     */
    public Object invoke(ResourceObject resourceObject,
            CallContext callContext, HiddenJaxRsRouter jaxRsRouter)
            throws MethodInvokeException, InvocationTargetException,
            MissingAnnotationException, WebApplicationException,
            RequestHandledException, NoMessageBodyReadersException,
            InstantiateParameterException {
        Annotation[][] parameterAnnotationss = javaMethod
                .getParameterAnnotations();
        Class<?>[] paramTypes = javaMethod.getParameterTypes();
        Type[] paramGenericTypes = javaMethod.getGenericParameterTypes();
        Object[] args = getParameterValues(paramTypes, paramGenericTypes,
                parameterAnnotationss, leaveEncoded, callContext, jaxRsRouter);
        try {
            Object jaxRsResourceObj = resourceObject.getJaxRsResourceObject();
            return this.javaMethod.invoke(jaxRsResourceObj, args);
        } catch (IllegalArgumentException e) {
            throw new MethodInvokeException("Could not invoke " + javaMethod, e);
        } catch (IllegalAccessException e) {
            throw new MethodInvokeException("Could not invoke " + javaMethod, e);
        }
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "["
                + this.javaMethod.getDeclaringClass().getSimpleName() + "."
                + this.javaMethod.getName() + "(__)]";
    }

    /**
     * @return Retuns the resource class
     */
    public ResourceClass getResourceClass() {
        return this.resourceClass;
    }

    /**
     * @return Returns the name of the method
     */
    public String getName() {
        Class<?>[] paramTypes = this.javaMethod.getParameterTypes();
        StringBuilder stb = new StringBuilder();
        stb.append(this.javaMethod.getName());
        stb.append('(');
        Util.append(stb, paramTypes);
        stb.append(')');
        return stb.toString();
    }

    /**
     * Returns the generic return type of the wrapped method.
     * 
     * @return the generic return type of the wrapped method.
     */
    public Type getGenericReturnType() {
        return javaMethod.getGenericReturnType();
    }

    /**
     * Returns the array of
     * 
     * @return
     */
    public Annotation[] getAnnotations() {
        return javaMethod.getAnnotations();
    }
}