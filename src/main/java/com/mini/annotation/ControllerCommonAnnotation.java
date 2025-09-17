package com.mini.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD) // 该注解用于方法
@Retention(RetentionPolicy.RUNTIME) // 注解在运行时可用，以便通过反射读取
@Documented
public @interface ControllerCommonAnnotation {
}
