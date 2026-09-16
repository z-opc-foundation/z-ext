package com.zifang.z.ext.annotation;

import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtPointDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

public class ExtScanner implements BeanFactoryPostProcessor, ApplicationContextAware {

    private String[] basePackages;
    private ApplicationContext applicationContext;

    public ExtScanner(String[] basePackages) {
        this.basePackages = basePackages;
    }

    public ExtScanner() {
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        scanExtPoints();
        scanExtImpls();
    }

    private void scanExtPoints() {
        if (basePackages == null || basePackages.length == 0) return;
        for (String basePackage : basePackages) {
            for (Class<?> clazz : scanClasses(basePackage, ExtPoint.class)) {
                registerExtPoint(clazz);
            }
        }
    }

    private void scanExtImpls() {
        if (basePackages == null || basePackages.length == 0) return;
        for (String basePackage : basePackages) {
            for (Class<?> clazz : scanClasses(basePackage, ExtImpl.class)) {
                registerExtImpl(clazz);
            }
        }
    }

    private void registerExtPoint(Class<?> clazz) {
        ExtPoint annotation = clazz.getAnnotation(ExtPoint.class);
        ExtPointDefinition def = new ExtPointDefinition();
        def.setPoint(annotation.value());
        def.setInterfaceClass(clazz);
        def.setType(annotation.type());
        def.setDescription(annotation.description());
        def.setVersion(annotation.version());
        def.setOrder(annotation.order());
        ExtRegistry.registerPoint(def);
    }

    private void registerExtImpl(Class<?> clazz) {
        ExtImpl annotation = clazz.getAnnotation(ExtImpl.class);
        ExtImplDefinition def = new ExtImplDefinition();
        def.setPoint(annotation.point());
        def.setName(annotation.name());
        def.setImplClass(clazz);
        def.setType(annotation.type());
        def.setDescription(annotation.description());
        def.setOrder(annotation.order());
        def.setEnabled(annotation.enabled());
        def.setCondition(annotation.condition());
        def.setWeight(annotation.weight());
        ExtRegistry.registerImpl(def);
    }

    private Set<Class<?>> scanClasses(String basePackage, Class<? extends Annotation> annotationClass) {
        Set<Class<?>> result = new HashSet<>();
        try {
            String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
                    + ClassUtils.convertClassNameToResourcePath(basePackage) + "/**/*.class";
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(packageSearchPath);
            MetadataReaderFactory readerFactory = new CachingMetadataReaderFactory();
            for (Resource resource : resources) {
                if (!resource.isReadable()) continue;
                MetadataReader reader = readerFactory.getMetadataReader(resource);
                String className = reader.getClassMetadata().getClassName();
                if (reader.getAnnotationMetadata().hasAnnotation(annotationClass.getName())) {
                    result.add(Class.forName(className));
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            // skip
        }
        return result;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
