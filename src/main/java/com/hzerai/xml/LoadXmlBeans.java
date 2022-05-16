/**
 * 
 */
package com.hzerai.xml;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.reflections.Reflections;

import com.google.common.collect.Lists;
import com.hzerai.FrameworkContext;
import com.hzerai.FrameworkException;
import com.hzerai.Scope;

/**
 * @author Habib Zerai
 *
 */
public class LoadXmlBeans {

	private static boolean intialized = false;
	private static Map<Class<?>, Object> xmlBeans = new HashMap<>();
	private static Map<Class<?>, Scope> xmlBeansScope = new HashMap<>();
	private static LoadXmlBeans xml;

	public static void load() {
		loadConfiguration();
	}

	private LoadXmlBeans() {

	}

	public static Map<Class<?>, Object> getAllBeans() {
		return Collections.unmodifiableMap(xmlBeans);
	}

	/**
	 * @param config
	 */
	private static void loadConfiguration() {
		if (intialized == true) {
			return;
		}
		intialized = true;
		try {
			Reflections reflections = new Reflections();
			Set<Class<?>> configurations = reflections.getTypesAnnotatedWith(Configuration.class);
			for (Class<?> config : configurations) {
				String xmlPath = config.getDeclaredAnnotation(Configuration.class).xmlPath();
				// beanDefinitionExample file under resources
				File xmlFile = new File(xmlPath);
				JAXBContext context = JAXBContext.newInstance(BeansDefinition.class);
				Unmarshaller unmarshaller = context.createUnmarshaller();
				BeansDefinition beansDefinition = (BeansDefinition) unmarshaller.unmarshal(xmlFile);
				loadBeansDefinition(beansDefinition);
			}

		} catch (Throwable e) {
			throw new FrameworkException("Exeption during loading xml beans", e);
		}
	}

	/**
	 * @param beansDefinition
	 * @throws ClassNotFoundException
	 * @throws InvocationTargetException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 */
	private static void loadBeansDefinition(BeansDefinition beansDefinition)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException,
			InvocationTargetException, NoSuchMethodException, SecurityException {
		for (BeanDefinition beanDef : beansDefinition.getBean()) {
			loadBean(beanDef);
		}
	}

	/**
	 * @param beanDef
	 * @return
	 * @throws ClassNotFoundException
	 * @throws InvocationTargetException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 */
	private static void loadBean(BeanDefinition beanDef)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException,
			InvocationTargetException, NoSuchMethodException, SecurityException {
		Object bean = null;
		List<PropertyDefinition> paramProperties = beanDef.getProperty();
		List<BeanDefinition> paramBeans = beanDef.getBean();
		for (BeanDefinition bd : paramBeans) {
			loadBean(bd);
		}
		String className = beanDef.getCls();
		String scope = beanDef.getScope();
		if (className == null) {
			throw new FrameworkException("found null class for bean definition in xml");
		}
		Class<?> clazz = Class.forName(className);
		Constructor<?> c = getConstructor(clazz, paramProperties, paramBeans);
		List<Object> params = getConstructorParams(c, paramProperties, paramBeans);
		bean = c.newInstance(params);
		Scope s = scope != null ? Scope.valueOf(scope) : Scope.Singleton;
		xmlBeansScope.put(clazz, s);
		xmlBeans.put(clazz, bean);
	}

	/**
	 * @param clazz
	 * @param paramProperties
	 * @param paramBeans
	 * @return
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 * @throws InvocationTargetException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws ClassNotFoundException
	 */
	private static List<Object> getConstructorParams(Constructor<?> c, List<PropertyDefinition> paramProperties,
			List<BeanDefinition> paramBeans)
			throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException, ClassNotFoundException {
		List<Object> allParams = new ArrayList<>(paramBeans);
		allParams.addAll(paramProperties);
		List<Object> sortedParams = Lists.newArrayList();
		for (Parameter p : c.getParameters()) {
			String paramName = p.getName();
			for (Object param : allParams) {
				if (param instanceof PropertyDefinition) {
					param = (PropertyDefinition) param;
					String pName = ((PropertyDefinition) param).getName();
					if (pName == paramName) {
						String pValue = ((PropertyDefinition) param).getValue();
						Class<?> paramType = p.getType();
						Object paramValue = paramType.getConstructor(String.class).newInstance(pValue);
						sortedParams.add(paramValue);
						continue;
					}
				} else {
					param = (BeanDefinition) param;
					String pName = ((BeanDefinition) param).getName();
					if (pName == paramName) {
						Class<?> paramType = Class.forName(((BeanDefinition) param).getCls());
						Object bean = xmlBeans.get(paramType);
						if (bean == null) {
							bean = FrameworkContext.getBean(paramType);
						}
						sortedParams.add(bean);
						continue;
					}
				}
			}
		}
		return null;
	}

	private static Constructor<?> getConstructor(Class<?> clazz, List<PropertyDefinition> paramProperties,
			List<BeanDefinition> paramBeans) {
		List<Object> allParams = new ArrayList<>(paramBeans);
		allParams.addAll(paramProperties);
		int paramSize = allParams.size();
		for (Constructor<?> c : clazz.getConstructors()) {
			if (c.getParameterCount() != paramSize) {
				continue;
			}
			int checkParams = 0;
			for (Parameter p : c.getParameters()) {
				String paramName = p.getName();
				for (Object param : allParams) {
					if (param instanceof PropertyDefinition) {
						param = (PropertyDefinition) param;
						String pName = ((PropertyDefinition) param).getName();
						if (pName == paramName) {
							checkParams++;
							continue;
						}
					} else {
						param = (BeanDefinition) param;
						String pName = ((BeanDefinition) param).getName();
						if (pName == paramName) {
							checkParams++;
							continue;
						}
					}
				}
				if (checkParams == c.getParameterCount()) {
					return c;
				}
			}
		}
		return null;
	}
}
