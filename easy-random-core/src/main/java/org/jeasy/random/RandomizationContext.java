/*
 * The MIT License
 *
 *   Copyright (c) 2020, Mahmoud Ben Hassine (mahmoud.benhassine@icloud.com)
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy
 *   of this software and associated documentation files (the "Software"), to deal
 *   in the Software without restriction, including without limitation the rights
 *   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *   copies of the Software, and to permit persons to whom the Software is
 *   furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *   THE SOFTWARE.
 */
package org.jeasy.random;

import org.jeasy.random.api.RandomizerContext;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Context object for a single call on {@link EasyRandom#nextObject(Class)}.
 * It contains a map acting as a cache of populated beans to avoid infinite recursion.
 *
 * @author Rémi Alvergnat (toilal.dev@gmail.com)
 */
class RandomizationContext implements RandomizerContext {

    private final EasyRandomParameters parameters;

    private final Map<Class<?>, List<Object>> populatedBeans;

    private final Stack<RandomizationContextStackItem> stack;

    private final Class<?> type;

    private Object rootObject;

    private final Map<Class<?>, Object> usedBeans = new HashMap<>();

    RandomizationContext(final Class<?> type, final EasyRandomParameters parameters) {
        this.type = type;
        populatedBeans = new IdentityHashMap<>();
        stack = new Stack<>();
        this.parameters = parameters;
    }

    void addPopulatedBean(final Class<?> type, Object object) {
        int objectPoolSize = parameters.getObjectPoolSize();
        List<Object> objects = populatedBeans.get(type);
        if (objects == null) {
            objects = new ArrayList<>(objectPoolSize);
        }
        if (objects.size() < objectPoolSize) {
            objects.add(object);
        }
        populatedBeans.put(type, objects);
    }

    Object getPopulatedBean(final Class<?> type) {
        int actualPoolSize = populatedBeans.get(type).size();
        int randomIndex = actualPoolSize > 1 ? nextInt(0, actualPoolSize) : 0;
        Object result = populatedBeans.get(type).get(randomIndex);
        if (parameters.isAvoidInfiniteRecursion()) {
            HashSet<Object> alreadyUsedBeans = getAlreadyUsedBeansOfType(type);
            if (alreadyUsedBeans.size() < actualPoolSize) {
                do {
                    result = populatedBeans.get(type).get(randomIndex);
                    randomIndex = (randomIndex + 1) % actualPoolSize;
                } while (alreadyUsedBeans.contains(result));
            }
        }
        return result;
    }

    boolean hasAlreadyRandomizedType(final Class<?> type) {
        return populatedBeans.containsKey(type) && populatedBeans.get(type).size() == parameters.getObjectPoolSize();
    }

    void pushStackItem(final RandomizationContextStackItem field) {
        stack.push(field);
    }

    void popStackItem() {
        stack.pop();
    }

    String getFieldFullName(final Field field) {
        List<String> pathToField = getStackedFieldNames();
        pathToField.add(field.getName());
        return String.join(".", toLowerCase(pathToField));
    }

    boolean hasExceededRandomizationDepth() {
        int currentRandomizationDepth = stack.size();
        return currentRandomizationDepth > parameters.getRandomizationDepth();
    }

    boolean useEmptyOnCurrentRandomizationDepth() {
        int currentRandomizationDepth = stack.size();
        return parameters.isAvoidNullsOnDeepestRecursionLevel() && currentRandomizationDepth == parameters.getRandomizationDepth();
    }

    private HashSet<Object> getAlreadyUsedBeansOfType(Class<?> type) {
        return Stream.concat(
                stack.stream().map(it -> it.getUsedBean(type)),
                usedBeans.get(type) == null ? Stream.empty() : Stream.of(usedBeans.get(type))
        ).collect(Collectors.toCollection(HashSet::new));
    }

    private List<String> getStackedFieldNames() {
        return stack.stream().map(i -> i.getField().getName()).collect(toList());
    }

    private List<String> toLowerCase(final List<String> strings) {
        return strings.stream().map(String::toLowerCase).collect(toList());
    }

    private int nextInt(int startInclusive, int endExclusive) {
        return startInclusive + new Random().nextInt(endExclusive - startInclusive);
    }

    void setRandomizedObject(Object randomizedObject) {
        if (this.rootObject == null) {
            this.rootObject = randomizedObject;
        }
    }

    @Override
    public Class<?> getTargetType() {
        return type;
    }

    @Override
    public Object getCurrentObject() {
        if (stack.empty()) {
            return rootObject;
        } else {
            return stack.lastElement().getObject();
        }
    }

    @Override
    public String getCurrentField() {
        return String.join(".", getStackedFieldNames());
    }

    @Override
    public int getCurrentRandomizationDepth() {
        return stack.size();
    }

    @Override
    public Object getRootObject() {
        return this.rootObject;
    }

    @Override
    public EasyRandomParameters getParameters() {
        return parameters;
    }

    public void registerBeanUsage(Class type, Object bean) {
        if (parameters.isAvoidInfiniteRecursion()) {
            if (stack.size() == 0) {
                usedBeans.put(type, bean);
            } else {
                stack.peek().registerBeanUsage(type, bean);
            }
        }
    }
}
