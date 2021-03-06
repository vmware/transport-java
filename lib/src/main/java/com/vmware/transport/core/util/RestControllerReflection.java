/*
 * Copyright 2018-2020 VMware, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 *
 */
package com.vmware.transport.core.util;

import org.springframework.context.ApplicationContext;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * Handles reflection magic for RestService.
 */
@Component
@SuppressWarnings("unchecked")
public class RestControllerReflection {

    private final ParameterNameDiscoverer parameterNameDiscoverer;

    private final ApplicationContext context;

    RestControllerReflection(
            ParameterNameDiscoverer parameterNameDiscoverer,
            ApplicationContext context
    ) {
        this.parameterNameDiscoverer = parameterNameDiscoverer;
        this.context = context;
    }

    /**
     * Locate RestControllers inside the Spring Context
     *
     * @return Map of all all controllers annotated with @RestController
     */
    Map<String, Object> locateRestControllers() {
        return context.getBeansWithAnnotation(RestController.class);
    }

    /**
     * Extract controller methods for @RequestMapping annotations.
     *
     * @param controller Controller to be looked at
     * @return Map of all method names to Method instances that use @RequestMapping
     */
    Map<String, Method> extractControllerRequestMappings(Object controller) {
        return extractControllerByAnnotation(controller, RequestMapping.class);
    }

    /**
     * Extract controller methods for @PatchMapping annotations.
     *
     * @param controller the Controller to be looked at
     * @return Map of all method names to Method instances that use @PatchMapping
     */
    Map<String, Method> extractControllerPatchMappings(Object controller) {
        return extractControllerByAnnotation(controller, PatchMapping.class);
    }

    /**
     * Extract controller methods for @GetMapping annotations.
     *
     * @param controller the Controller to be looked at
     * @return Map of all method names to Method instances that use @GetMapping
     */
    Map<String, Method> extractControllerGetMappings(Object controller) {
        return extractControllerByAnnotation(controller, GetMapping.class);
    }

    /**
     * Extract controller methods for @PostMapping annotations.
     *
     * @param controller the Controller to be looked at
     * @return Map of all method names to Method instances that use @PostMapping
     */
    Map<String, Method> extractControllerPostMappings(Object controller) {
        return extractControllerByAnnotation(controller, PostMapping.class);
    }

    /**
     * Extract controller methods for @PutMapping annotations.
     *
     * @param controller the Controller to be looked at
     * @return Map of all method names to Method instances that use @PutMapping
     */
    Map<String, Method> extractControllerPutMappings(Object controller) {
        return extractControllerByAnnotation(controller, PutMapping.class);
    }

    /**
     * Extract controller methods for @DeleteMapping annotations.
     *
     * @param controller the Controller to be looked at
     * @return Map of all method names to Method instances that use @DeleteMapping
     */
    Map<String, Method> extractControllerDeleteMappings(Object controller) {
        return extractControllerByAnnotation(controller, DeleteMapping.class);
    }

    /**
     * Extract controller by a specific Annotation type
     *
     * @param controller     the Controller to be looked at
     * @param annotationType the Class of the annotation to be searched for
     * @return a Map of all controllers using that annotation
     */
    private Map<String, Method> extractControllerByAnnotation(Object controller, Class annotationType) {
        List<Method> rawMethods = Arrays.asList(controller.getClass().getDeclaredMethods());
        Map<String, Method> cleanedMethods = new HashMap<>();

        for (Method method : rawMethods) {

            if (method.getAnnotationsByType(annotationType) != null) {
                cleanedMethods.put(method.getName(), method);
            }
        }

        return cleanedMethods;
    }

    /**
     * Extract a method's args/parameters and the types of those arguments
     *
     * @param method the Method to be looked at
     * @return a Map of all method parameters and the class types
     */

    Map<String, Class> extractMethodParameters(Method method) {
        String[] params = parameterNameDiscoverer.getParameterNames(method);
        Map<String, Class> paramMap = new HashMap<>();

        int index = 0;
        if (params != null) {
            for (String param : params) {
                paramMap.put(param, method.getParameterTypes()[index]);
                index++;
            }
        }
        return paramMap;
    }

    /**
     * Extract an ordered list of the paramter/arg names for a method.
     *
     * @param method the Method to be looked at
     * @return a List of all the method parameters (ordered)
     */
    List<String> extractMethodParameterList(Method method) {
        String[] params = parameterNameDiscoverer.getParameterNames(method);
        List<String> paramList = new ArrayList<>();

        if (params != null)
            Collections.addAll(paramList, params);

        return paramList;
    }

    /**
     * Extract the method's arguments annotations (if used)
     *
     * @param method the Method to be looked at
     * @return Map of all method argument name and annotation types
     */
    Map<String, Class> extractMethodAnnotationTypes(Method method) {
        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
        Parameter[] params = method.getParameters();
        Map<String, Class> paramMap = new HashMap<>();

        if (params != null) {
            for (int x = 0; x < params.length; x++) {
                if (params[x].getAnnotations() != null && params[x].getAnnotations().length >= 1) {
                    paramMap.put(paramNames[x], params[x].getAnnotations()[0].annotationType());
                } else {
                    paramMap.put(paramNames[x], null);
                }
            }
        }
        return paramMap;
    }

    /**
     * Extract all annotation object value for a methods args/params annotation.
     *
     * @param method the Method to be looked at
     * @return a Map of all method annotation argument name and value
     */
    Map<String, Object> extractMethodAnnotationValues(Method method) {
        Parameter[] params = method.getParameters();
        String[] paramNames = this.parameterNameDiscoverer.getParameterNames(method);
        Map<String, Object> paramMap = new HashMap<>();

        for (int x = 0; x < params.length; x++) {

            if (params[x].getAnnotations() != null && params[x].getAnnotations().length >= 1) {
                paramMap.put(paramNames[x],
                        this.extractMethodAnnotation(params[x],
                                params[x].getAnnotations()[0].annotationType())
                );
            } else {
                paramMap.put(params[x].getName(), null);
            }
        }
        return paramMap;
    }

    /**
     * Extract a specific annotation value from a method.
     *
     * @param param the Parameter to be evaluated
     * @param annotation the Annotation to be evaluated
     * @return The annotation value (that can then be inspected)
     */
    private Object extractMethodAnnotation(Parameter param, Class annotation) {

        switch (annotation.getName()) {
            case "org.springframework.web.bind.annotation.PathVariable":
                return param.getAnnotation(PathVariable.class);

            case "org.springframework.web.bind.annotation.RequestParam":
                return param.getAnnotation(RequestParam.class);

            case "org.springframework.web.bind.annotation.RequestBody":
                return param.getAnnotation(RequestBody.class);

            case "org.springframework.web.bind.annotation.RequestHeader":
                return param.getAnnotation(RequestHeader.class);

            default:
                return null;

        }

    }

}
