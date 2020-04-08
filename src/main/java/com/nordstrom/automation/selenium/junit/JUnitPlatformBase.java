package com.nordstrom.automation.selenium.junit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.openqa.selenium.WebDriver;

import com.nordstrom.automation.selenium.exceptions.PlatformActivationFailedException;
import com.nordstrom.automation.selenium.platform.PlatformEnum;
import com.nordstrom.automation.selenium.platform.PlatformTargetable;
import com.nordstrom.common.base.UncheckedThrow;
import com.nordstrom.common.file.PathUtils;

public abstract class JUnitPlatformBase<P extends Enum<?> & PlatformEnum> extends JUnitBase implements PlatformTargetable<P> {
    
    private final Class<P> platformClass;
    private final Method fromString;
    private final Method values;

    public JUnitPlatformBase(Class<P> platformClass) {
        this.platformClass = platformClass;
        try {
            fromString = platformClass.getMethod("fromString", String.class);
            values = platformClass.getMethod("values");
        } catch (NoSuchMethodException | SecurityException e) {
            throw UncheckedThrow.throwUnchecked(e);
        }
    }

    @Override
    public String[] getSubPath() {
        return PathUtils.append(getTargetPlatform().getName());
    }

    @Override
    @SuppressWarnings("unchecked")
    public P getTargetPlatform() {
        return (P) targetPlatformRule.getPlatform();
    }

    @Override
    public void activatePlatform(WebDriver driver, P platform)
                    throws PlatformActivationFailedException {
        // by default, do nothing
    }
    
    @Override
    public P[] getValidPlatforms() {
        return (P[]) values();
    }

    @Override
    public P platformFromString(String name) {
        return invoke(fromString, name);
    }

    @Override
    public Class<P> getPlatformType() {
        return platformClass;
    }
    
    public P fromString(String name) {
        for (P platform : values()) {
            if (platform.getName().equals(name)) {
                return platform;
            }
        }
        return null;
    }
    
    private P[] values() {
        return invoke(values);
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(Method method, Object... parameters) {
        try {
            return (T) method.invoke(null, parameters);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw UncheckedThrow.throwUnchecked(e);
        }
    }

}
