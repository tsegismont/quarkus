package org.jboss.resteasy.reactive.server;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.UriInfo;

/**
 * When used on a method, then an implementation of {@link jakarta.ws.rs.ext.ExceptionMapper} is generated
 * that calls the annotated method with the proper arguments.
 * <p>
 * When the annotation is placed on a method that is not a JAX-RS Resource class, the method handles exceptions in global
 * fashion (as do regular JAX-RS {@code ExceptionMapper} implementations).
 * However, when it is placed on a method of a JAX-RS Resource class, the method is only used to handle exceptions originating
 * from
 * that JAX-RS Resource class.
 * Methods in a JAX-RS class annotated with this annotation will be used first when determining how to handle a thrown
 * exception.
 * This means that these methods take precedence over the global {@link jakarta.ws.rs.ext.ExceptionMapper} classes.
 * <p>
 * In addition to the exception being handled, an annotated method can also declare any of the following
 * parameters (in any order):
 * <ul>
 * <li>{@link ContainerRequestContext}
 * <li>{@link UriInfo}
 * <li>{@link HttpHeaders}
 * <li>{@link Request}
 * <li>{@link ResourceInfo}
 * <li>{@link SimpleResourceInfo}
 * </ul>
 *
 * When {@code value} is not set, then the handled Exception type is deduced by the Exception type used in the method parameters
 * (there must be exactly one Exception type in this case).
 * <p>
 * The return type of the method must be either be of type {@code Response}, {@code Uni<Response>}, {@code RestResponse} or
 * {@code Uni<RestResponse>}.
 * <p>
 * See also {@link UnwrapException}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ServerExceptionMapper {

    Class<? extends Throwable>[] value() default {};

    /**
     * The priority with which the exception mapper will be executed.
     * <p>
     * They are sorted in ascending order; the lower the number the higher the priority.
     *
     * @see Priorities
     */
    int priority() default Priorities.USER;
}
