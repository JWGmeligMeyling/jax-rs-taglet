package nl.tudelft.ewi.javax;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

/*
 * READ ALERT
 *    THE CODE BELOW IS TERRIBLE. I AM NOT RESPONSIBLE FOR ANY EYE INJURIES.
 *    FEEL FREE TO THROW IT AWAY AND SUBMIT PATCHES.
 */


/**
 * A class that initializes POJO's with some fields predefined.
 *
 * @author Jan-Willem Gmelig Meyling
 */
public class POJOInitializer {

	private static final List<Class<?>> FLOATY = Arrays.asList(new Class<?>[]{float.class, double.class, Float.class, Double.class});
	private static final  List<Class<?>> INTY = Arrays.asList(new Class<?>[] { int.class, short.class, byte.class, long.class,
		Integer.class, Short.class, Byte.class, Long.class });

	private final Map<Type, Object> instances = new HashMap<>();

	/**
	 * Create an instance of Type with some values set.
	 * @param type {@code Type} to set the information for.
	 * @return The instantiated object.
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws NoSuchFieldException
	 */
	public Object initializeTestData(Type type) throws IllegalAccessException, InstantiationException, NoSuchFieldException {
		System.out.println("POJOInitializer#initializeTestData");
		if(instances.containsKey(type)) {
			return instances.get(type);
		}

		Object instance = null;
		Class<?> clasz = (Class<?>) (type instanceof ParameterizedType ? ((ParameterizedType) type).getRawType() : type);

		try {
			if(Enum.class.isAssignableFrom(clasz)) {
				Enum<?>[] enumConstants = ((Class<? extends Enum<?>>) clasz).getEnumConstants();
				instance = enumConstants != null && enumConstants.length > 0 ? enumConstants[0] : null;
			}
			else if(Collection.class.isAssignableFrom(clasz)) {
				instance = createCollection(type);
			}
			else if (Map.class.isAssignableFrom(clasz)) {
				instance = createMap(type);
			}
			else if(FLOATY.contains(clasz)) {
				instance =  Math.random() * 100;
			}
			else if (INTY.contains(clasz)) {
				instance = 1;
			}
			else if (String.class.equals(clasz)) {
				instance = "lupa";
			}
			else if (!Modifier.isAbstract(clasz.getModifiers())){
				instance = createPOJO(clasz);
			}
			else {
				instance = createAbstractPOJO(clasz);
			}
		}
		catch (Throwable t) {
			t.printStackTrace();
		}

		instances.put(type, instance);
		return instance;
	}

	@SuppressWarnings("unchecked")
	private Object createMap(java.lang.reflect.Type type) throws IllegalAccessException, InstantiationException, NoSuchFieldException {
		System.out.println("POJOInitializer#createMap");
		Map map = new HashMap();
		if(type instanceof ParameterizedType) {
			ParameterizedType parameterizedType = (ParameterizedType) type;
			java.lang.reflect.Type[] typeArguments = parameterizedType.getActualTypeArguments();
			map.put(initializeTestData(typeArguments[0]), initializeTestData(typeArguments[1]));
		}
		return map;
	}

	@SuppressWarnings("unchecked")
	private Object createCollection(java.lang.reflect.Type type) throws IllegalAccessException, InstantiationException, NoSuchFieldException {
		System.out.println("POJOInitializer#createCollection");
		Collection collection;
		if (type instanceof ParameterizedType) {
			ParameterizedType parameterizedType = (ParameterizedType) type;
			collection = Set.class.isAssignableFrom((Class<?>) parameterizedType.getRawType()) ? new HashSet() : new ArrayList();
			java.lang.reflect.Type elementType = parameterizedType.getActualTypeArguments()[0];
			collection.add(initializeTestData(elementType));
			if (FLOATY.contains(elementType)) {
				collection.add(initializeTestData(elementType));
				collection.add(initializeTestData(elementType));
			}
		} else {
			collection = Set.class.isAssignableFrom((Class<?>) type) ? new HashSet() : new ArrayList();
		}
		return collection;
	}

	private final Stack<Class<?>> seenSubTypes = new Stack<>();

	private Object createAbstractPOJO(Class<?> clasz) throws IllegalAccessException, InstantiationException, NoSuchFieldException {

		System.out.println("POJOInitializer#createAbstractPOJO");
		// Ignore equal classes to prevent infite looping in recursive strategies:
		// Feature -> Feature -> Feature -> ...  => Feature -> Polygon
		seenSubTypes.push(clasz);

		Object instance = null;
		for(Class<?> annotatedClasz = clasz; annotatedClasz != null && !annotatedClasz.equals(Object.class); annotatedClasz = clasz.getSuperclass()) {
			JsonSubTypes jsonSubTypes = annotatedClasz.getAnnotation(JsonSubTypes.class);
			if(jsonSubTypes != null) {
				for(JsonSubTypes.Type jsonSubType : jsonSubTypes.value()) {
					Class<?> jsonSubTypeClass = jsonSubType.value();
					if(!seenSubTypes.contains(clasz) && clasz.isAssignableFrom(jsonSubTypeClass)) {
						instance = createPOJO(clasz);
						break;
					}
				}
			}
		}

		seenSubTypes.pop();
		return instance;
	}

	private Object createPOJO(Class<?> clasz) throws IllegalAccessException, InstantiationException {
		System.out.println("POJOInitializer#createPOJO");
		Object instance = clasz.newInstance();
		instances.put(clasz, instance); // Prevent creating dupes...
		for(; clasz != null && !clasz.equals(Object.class); clasz = clasz.getSuperclass()) {
			for(Field field : clasz.getDeclaredFields()) {
				if (field.isAnnotationPresent(JsonIgnoreProperties.class) ||
					field.isAnnotationPresent(JsonIgnore.class)) {
					continue;
				}
				try {
					field.setAccessible(true);
					field.set(instance, initializeTestData(field.getGenericType()));
				}
				catch (Exception e) {
					// Swallow exceptions here...
					e.printStackTrace();
				}
			}
		}
		return instance;
	}

}