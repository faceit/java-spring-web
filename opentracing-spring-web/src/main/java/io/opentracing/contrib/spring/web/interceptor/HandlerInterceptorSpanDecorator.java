/**
 * Copyright 2016-2019 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.opentracing.contrib.spring.web.interceptor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

import io.opentracing.Span;

/**
 * SpanDecorator to decorate span at different stages in filter processing.
 *
 * @author Pavol Loffay
 */
public interface HandlerInterceptorSpanDecorator {

    /**
     * This is called in
     * {@link org.springframework.web.servlet.HandlerInterceptor#preHandle(HttpServletRequest, HttpServletResponse, Object)}.
     *
     * @param httpServletRequest
     *            request
     * @param handler
     *            handler
     * @param span
     *            current span
     */
    void onPreHandle(HttpServletRequest httpServletRequest, Object handler, Span span);

    /**
     * This is called in
     * {@link org.springframework.web.servlet.HandlerInterceptor#afterCompletion(HttpServletRequest, HttpServletResponse, Object, Exception)}
     *
     * @param httpServletRequest
     *            request
     * @param httpServletResponse
     *            response
     * @param handler
     *            handler
     * @param ex
     *            exception
     * @param span
     *            current span
     */
    void onAfterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
            Object handler, Exception ex, Span span);

    /**
     * This is called in
     * {@link org.springframework.web.servlet.AsyncHandlerInterceptor#afterConcurrentHandlingStarted(HttpServletRequest, HttpServletResponse, Object)}
     *
     * @param httpServletRequest
     *            request
     * @param httpServletResponse
     *            response
     * @param handler
     *            handler
     * @param span
     *            current span
     */
    void onAfterConcurrentHandlingStarted(HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse, Object handler, Span span);

    /**
     * Decorator to record details about the handler as log events recorded on
     * the span.
     */
    HandlerInterceptorSpanDecorator STANDARD_LOGS = new HandlerInterceptorSpanDecorator() {

        @Override
        public void onPreHandle(HttpServletRequest httpServletRequest, Object handler, Span span) {
            if (!(handler instanceof HandlerMethod)) {
                return;
            }

            Map<String, Object> logs = new HashMap<>(3);
            logs.put("event", "preHandle");
            logs.put(HandlerUtils.HANDLER, handler);

            String metaData = HandlerUtils.className(handler);
            if (metaData != null) {
                logs.put(HandlerUtils.HANDLER_CLASS_NAME, metaData);
            }

            metaData = HandlerUtils.methodName(handler);
            if (metaData != null) {
                logs.put(HandlerUtils.HANDLER_METHOD_NAME, metaData);
            }

            span.log(logs);
        }

        @Override
        public void onAfterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                Object handler, Exception ex, Span span) {
            Map<String, Object> logs = new HashMap<>(2);
            logs.put("event", "afterCompletion");
            logs.put(HandlerUtils.HANDLER, handler);
            span.log(logs);
        }

        @Override
        public void onAfterConcurrentHandlingStarted(HttpServletRequest httpServletRequest,
                HttpServletResponse httpServletResponse, Object handler, Span span) {
            Map<String, Object> logs = new HashMap<>(2);
            logs.put("event", "afterConcurrentHandlingStarted");
            logs.put(HandlerUtils.HANDLER, handler);
            span.log(logs);
        }
    };

    /**
     * Use the handler's method name as the span's operation name.
     */
    HandlerInterceptorSpanDecorator HANDLER_METHOD_OPERATION_NAME = new HandlerInterceptorSpanDecorator() {

        @Override
        public void onPreHandle(HttpServletRequest httpServletRequest, Object handler, Span span) {
            if (!(handler instanceof HandlerMethod)) {
                return;
            }

            String metaData = HandlerUtils.getOperationNameFromClassAndMethod(handler);
            if (metaData != null) {
                span.setOperationName(metaData);
            }
        }

        @Override
        public void onAfterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                Object handler, Exception ex, Span span) {
        }

        @Override
        public void onAfterConcurrentHandlingStarted(HttpServletRequest httpServletRequest,
                HttpServletResponse httpServletResponse, Object handler, Span span) {
        }
    };

    /**
     * Use the Spring RequestMapping's value as the span's operation name.
     */
    HandlerInterceptorSpanDecorator HANDLER_REQUEST_MAPPING_OPERATION_NAME = new HandlerInterceptorSpanDecorator() {

        @Override
        public void onPreHandle(HttpServletRequest httpServletRequest, Object handler, Span span) {
            if (!(handler instanceof HandlerMethod)) {
                return;
            }

            String webOperationName = HandlerUtils.getOperationNameFromAnnotation(handler);
            if (webOperationName != null) {
                span.setOperationName(webOperationName);
                return;
            }

            String metaData = HandlerInterceptorSpanDecorator.HandlerUtils.getOperationNameFromClassAndMethod(handler);
            if (metaData != null) {
                span.setOperationName(metaData);
            }
        }

        @Override
        public void onAfterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                Object handler, Exception ex, Span span) {
        }

        @Override
        public void onAfterConcurrentHandlingStarted(HttpServletRequest httpServletRequest,
                HttpServletResponse httpServletResponse, Object handler, Span span) {
        }
    };

    /**
     * Helper class for deriving tags/logs from handler object.
     */
    class HandlerUtils {
        private HandlerUtils() {
        }

        /**
         * Class name of a handler serving request
         */
        public static final String HANDLER_CLASS_NAME = "handler.class_simple_name";
        /**
         * Method name of handler serving request
         */
        public static final String HANDLER_METHOD_NAME = "handler.method_name";
        /**
         * Spring handler object
         */
        public static final String HANDLER = "handler";

        public static String className(Object handler) {
            return handler instanceof HandlerMethod ? ((HandlerMethod) handler).getBeanType().getSimpleName()
                    : handler.getClass().getSimpleName();
        }

        public static String methodName(Object handler) {
            return handler instanceof HandlerMethod ? ((HandlerMethod) handler).getMethod().getName() : null;
        }

        public static String requestMapping(Object handler) {
            String[] mappings = ((HandlerMethod) handler).getMethodAnnotation(RequestMapping.class).value();
            return Arrays.toString(mappings);
        }

        public static String methodRequestMapping(Object handler) {
            String[] mappings = null;

            RequestMapping annotation = ((HandlerMethod) handler).getMethodAnnotation(RequestMapping.class);
            if (annotation != null) {
                mappings = annotation.value();
            }

            return mappings != null && mappings.length > 0 ? mappings[0] : null;
        }

	    public static String clazzRequestMapping(Object handler) {
		    String[] mappings = null;

		    RequestMapping annotation = ((HandlerMethod) handler).getBeanType().getAnnotation(RequestMapping.class);
		    if (annotation != null) {
			    mappings = annotation.value();
		    }

		    return mappings != null && mappings.length > 0 ? mappings[0] : null;
	    }

        public static String getOperationNameFromClassAndMethod(Object handler) {
            return new StringBuffer(className(handler)).append("#").append(methodName(handler)).toString();
        }

        public static String getOperationNameFromAnnotation(Object handler) {
            StringBuffer operationName = new StringBuffer();

            String clazzRequestMapping = clazzRequestMapping(handler);
            if (clazzRequestMapping != null) {
                operationName.append(clazzRequestMapping);
            }
            String methodRequestMapping = methodRequestMapping(handler);
            if (methodRequestMapping != null) {
                operationName.append(methodRequestMapping);
            }

            return operationName == null ? null : operationName.toString();
        }
    }

}
