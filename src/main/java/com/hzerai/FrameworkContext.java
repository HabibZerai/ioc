/**
 * 
 */
package com.hzerai;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.reflections.Reflections;
import org.reflections.scanners.Scanner;

import com.hzerai.xml.LoadXmlBeans;

/**
 * @author Habib Zerai
 *
 */
public final class FrameworkContext {

	private static FrameworkContext context;
	private static boolean intialized = false;

	private static String scannedPath = "";
	private static Reflections reflections;
	private static Set<Class<?>> loadedBeans = new HashSet<>();
	private static Map<Class<?>, Object> beans = new HashMap<>();
	private static Map<Class<?>, Set<Object>> implementations = new HashMap<>();
	private static Map<Class<?>, Scope> beansScope = new HashMap<>();

	public static void run() {
		reflections = new Reflections();
		createContext();
	}

	private FrameworkContext() {

	}

	public static void run(String path, boolean loadXmlBeans) {
		if (loadXmlBeans)
			LoadXmlBeans.load();
		scannedPath = path;
		reflections = new Reflections(scannedPath);
		createContext();
	}

	public static void run(String path, boolean loadXmlBeans, Scanner... scanners) {
		if (loadXmlBeans)
			LoadXmlBeans.load();
		scannedPath = path;
		reflections = new Reflections(scannedPath, scanners);
		createContext();
	}

	private static void createContext() {
		try {
			if (intialized == true) {
				return;
			}
			intialized = true;
			init();
			handleAutowire();
		} catch (Throwable e) {
			throw new FrameworkException("Exception during the initialization of the FramekwrokContext", e);
		}
	}

	public static Object getBean(Class<?> clazz) {
		Scope beanScope = beansScope.get(clazz);
		if (Scope.Prototype.equals(beanScope) && loadedBeans.contains(clazz)) {
			return prototype(clazz);
		}
		return beans.get(clazz);
	}

	public static Map<Class<?>, Object> getAllBeans() {
		return Collections.unmodifiableMap(beans);
	}

	public static Object getBean(String className) throws ClassNotFoundException {
		Class<?> clazz;
		clazz = Class.forName(className);
		return getBean(clazz);
	}

	public static Set<Object> getImplementations(Class<?> superClass) {
		return implementations.get(superClass);
	}

	private static void init() throws InstantiationException, IllegalAccessException, IllegalArgumentException,
			InvocationTargetException, NoSuchMethodException, SecurityException {
		loadedBeans = reflections.getTypesAnnotatedWith(Component.class);
		initBeans(loadedBeans);
	}

	private static void initBeans(Set<Class<?>> classes) throws InstantiationException, IllegalAccessException,
			IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		for (Class<?> cls : classes) {
			// handle constructors with params later in autowiring part
			if (cls.getConstructor().getParameterCount() > 0) {
				continue;
			}
			Object o = cls.getConstructor().newInstance();
			beans.put(cls, o);
			Scope scope = cls.getAnnotation(Component.class).scope();
			beansScope.put(cls, scope);
			handleSupers(o);

		}
	}

	private static void handleAutowire() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException,
			SecurityException, InstantiationException, InvocationTargetException {
		// inject into field declaration
		for (Field f : reflections.getFieldsAnnotatedWith(Autowired.class)) {
			autowireField(f);
		}
		// inject into setterName
		for (Method f : reflections.getMethodsAnnotatedWith(Autowired.class)) {
			Class<?> container = f.getDeclaringClass();
			String setterMethodName = f.getName();
			String fieldName = setterMethodName.substring(4);
			fieldName = setterMethodName.substring(3, 3).toLowerCase() + fieldName;
			autowireField(container.getDeclaredField(fieldName));
		}
		// inject into constructors and create beans (for now it's not optimal,
		// might skip some beans.. )
		for (Constructor<?> c : reflections.getConstructorsWithAnyParamAnnotated(Autowired.class)) {
			ArrayList<Object> params = new ArrayList<>();
			for (Parameter p : c.getParameters()) {
				if (p.isAnnotationPresent(Autowired.class)) {
					params.add(beans.get(p.getType()));
				}
			}
			if (c.getParameterCount() != params.size()) {
				continue;
			}
			Object beanWithParamConstructor = c.newInstance(params);
			beans.put(beanWithParamConstructor.getClass(), beanWithParamConstructor);
			Scope scope = beanWithParamConstructor.getClass().getAnnotation(Component.class).scope();
			beansScope.put(beanWithParamConstructor.getClass(), scope);
			handleSupers(beanWithParamConstructor);
		}
	}

	private static Object prototype(Class<?> clazz) {
		try {
			Object bean = clazz.getConstructor().newInstance();
			for (Field f : bean.getClass().getDeclaredFields()) {
				if (f.isAnnotationPresent(Autowired.class)) {
					Class<?> fieldClass = f.getType();
					Object fieldBean = getBean(fieldClass);
					boolean oldAccess = f.canAccess(bean);
					f.setAccessible(true);
					f.set(bean, fieldBean);
					f.setAccessible(oldAccess);
				}
			}
			for (Method f : bean.getClass().getDeclaredMethods()) {
				if (f.isAnnotationPresent(Autowired.class)) {
					Class<?> container = f.getDeclaringClass();
					String setterMethodName = f.getName();
					String fieldName = setterMethodName.substring(4);
					fieldName = setterMethodName.substring(3, 3).toLowerCase() + fieldName;
					Field field = container.getDeclaredField(fieldName);
					Class<?> fieldClass = field.getType();
					Object fieldBean = getBean(fieldClass);
					boolean oldAccess = f.canAccess(bean);
					field.setAccessible(true);
					field.set(bean, fieldBean);
					field.setAccessible(oldAccess);
				}
			}
			return bean;
		} catch (Throwable e) {
			throw new FrameworkException("Exception during the initialization of the FramekwrokContext", e);
		}
	}

	private static void autowireField(Field f) throws IllegalArgumentException, IllegalAccessException {
		Class<?> container = f.getDeclaringClass();
		Class<?> fieldClass = f.getType();
		Object containerBean = getBean(container);
		Object fieldBean = getBean(fieldClass);
		if (containerBean == null || fieldBean == null) {
			return;
		}
		boolean oldAccess = f.canAccess(containerBean);
		f.setAccessible(true);
		f.set(containerBean, fieldBean);
		f.setAccessible(oldAccess);
	}

	private static void handleSupers(Object o) {
		Class<?> superClass = o.getClass().getSuperclass();
		if (implementations.containsKey(superClass)) {
			implementations.get(superClass).add(o);
		} else {
			implementations.put(superClass, new HashSet<>());
			implementations.get(superClass).add(o);
		}
	}

}
