package org.jboss.modules;

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Various methods for obtaining caller info.
 *
 * @author Jason T. Greene
 */
class CallerContext {
    private final static class Hack extends SecurityManager {
        @Override
        protected Class<?>[] getClassContext() {
            return super.getClassContext();
        }
    }

    private static Hack hack = AccessController.doPrivileged(new PrivilegedAction<Hack>() {
        @Override
        public Hack run() {
            return new Hack();
        }
    });

    static Class<?> getCallingClass() {
        Class<?> stack[] = hack.getClassContext();
        int i = 3;
        while (stack[i] == stack[2]) {
            // skip nested calls fromt the same class
            if (++i >= stack.length)
                return null;
        }

        return stack[i];
    }
}
