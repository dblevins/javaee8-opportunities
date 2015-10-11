/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.supertribe.interceptors.interim.stack;

import javax.interceptor.InvocationContext;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class InterceptorStack {
    private final Object beanInstance;
    private final Method targetMethod;
    private List<Interception> interceptions;

    public InterceptorStack(final Object beanInstance, final Method targetMethod, final List<Interception> interceptions) {
        this.beanInstance = beanInstance;
        this.targetMethod = targetMethod;
        this.interceptions = interceptions;
    }

    public Object invoke(final Object... parameters) throws Exception {
        final InvocationContext context = new FunctionalInvocationContext(beanInstance, targetMethod, interceptions, parameters);
        return context.proceed();
    }

    public static class FunctionalInvocationContext implements InvocationContext {

        private final Iterator<Interception> invocations;
        private final Object target;
        private final Method method;
        private final Object[] parameters;
        private final Map<String, Object> contextData = new TreeMap<>();
        private final Class<?>[] parameterTypes;
        private final Invocation invocation;

        public FunctionalInvocationContext(final Object target, final Method method, final List<Interception> interceptions, final Object... parameters) {
            this.target = Objects.requireNonNull(target);
            this.method = Objects.requireNonNull(method);
            this.parameters = Objects.requireNonNull(parameters);
            this.parameterTypes = method.getParameterTypes();

            invocations = interceptions.iterator();
            invocation = ()-> method.invoke(this.target, this.parameters);
        }

        @Override
        public Object getTimer() {
            return null;
        }

        @Override
        public Object getTarget() {
            return target;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Constructor<?> getConstructor() {
            throw new IllegalStateException();
        }

        @Override
        public Object[] getParameters() {
            return this.parameters;
        }

        @Override
        public void setParameters(final Object[] fromParameters) {
            Parameters.overwrite(fromParameters, this.parameters, this.parameterTypes);
        }

        @Override
        public Map<String, Object> getContextData() {
            return contextData;
        }

        @Override
        public Object proceed() throws Exception {
            try {
                if (invocations.hasNext()) {
                    Interception result = invocations.next();
                    return result.invoke(this);
                } else {
                    return invocation.invoke();
                }
            } catch (final InvocationTargetException e) {
                throw unwrapCause(e);
            }
        }

        /**
         * Business method interceptors can only throw exception allowed by the target business method.
         * Lifecycle interceptors can only throw RuntimeException.
         *
         * @param e the invocation target exception of a reflection method invoke
         * @return the cause of the exception
         * @throws AssertionError if the cause is not an Exception or Error.
         */
        private Exception unwrapCause(final Exception e) {
            final Throwable cause = e.getCause();
            if (cause == null) {
                return e;
            } else if (cause instanceof Exception) {
                return (Exception) cause;
            } else if (cause instanceof Error) {
                throw (Error) cause;
            } else {
                throw new AssertionError(cause);
            }
        }

        public String toString() {
            final String methodName = method != null ? method.getName() : null;

            return "InvocationContext(target=" + target.getClass().getName() + ", method=" + methodName + ")";
        }
    }

}
